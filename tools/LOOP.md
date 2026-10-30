# Improvement loop log

Each hourly run reads this file, picks the weakest area, ships one upgrade, and appends a line here.
Scorecard areas (rate 1-5, lowest first): world life, AI behaviour, graphics, combat/enemies, critters, endgame/progression, UI/follow view, performance, bugs.

## Runs
- 2026-10-30 | endgame/legends | legends were never bound (won battles let them leave); legends now always get a pact attempt (up to 6 tries, rising odds, ignore village cap), pact celebrated village-wide | legends bound in 8h BOOST=4 run: 0 -> 2, legacy goals 6/8 -> 7/8 | (first run, manual)
- 2026-10-30 | world life (weather/seasons) | seasons now follow village days (3 days each, not the phone calendar); in-game weather: rain, thunderstorms with lightning strikes + flashes, fog, snow, post-storm rainbow, harvest festival in autumn, winter slows farms/bushes, season chip in corner; talk/prompts reflect real weather+season | weather/season event kinds in 6h run: 1 (rain only) -> 11 distinct (88 lightning, 11 storms, 11 fog, 11 snow, 7 rainbows, 4 full years), 0 sim errors, idle share unchanged (~same as baseline), village survives/grows | v1.0.18
