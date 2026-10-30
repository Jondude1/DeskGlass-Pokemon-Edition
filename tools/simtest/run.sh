#!/bin/bash
# Headless village simulation. Usage: tools/simtest/run.sh <game-hours> ; env BOOST=2|3|4 starts at that age.
# Compiles only the sim package (no Android) with the Kotlin compiler bundled in Gradle's lib folder.
set -e
HERE="$(cd "$(dirname "$0")" && pwd)"
R="$HERE/../../app/src/main/java/com/jondude1/deskglass"
L=$(ls -d /opt/gradle-*/lib 2>/dev/null | head -1)
[ -z "$L" ] && L=$(dirname "$(find / -name 'kotlin-compiler-embeddable-*.jar' 2>/dev/null | head -1)")
CC=$(ls $L/kotlin-compiler-embeddable-*.jar | head -1); V=$(basename $CC .jar | sed 's/kotlin-compiler-embeddable-//')
CP=$CC:$L/kotlin-stdlib-$V.jar:$L/kotlin-script-runtime-$V.jar:$L/kotlin-reflect-$V.jar:$L/kotlin-daemon-embeddable-$V.jar:$(ls $L/trove4j-*.jar):$(ls $L/annotations-*.jar | head -1):$(ls $L/kotlinx-coroutines-core-jvm-*.jar | head -1)
CO=$(ls $L/kotlinx-coroutines-core-jvm-*.jar | head -1)
OUT=$(mktemp -d)
java -cp $CP org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -cp $L/kotlin-stdlib-$V.jar:$CO -d $OUT $R/sim/*.kt $HERE/stubs/org/json/Json.kt $HERE/SimMain.kt 2>&1 | grep -E "error" | head
java -cp $OUT:$L/kotlin-stdlib-$V.jar:$CO SimMainKt "$1" 2>&1 | grep -v JAVA_TOOL
