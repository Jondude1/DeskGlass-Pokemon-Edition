# DeskGlass Pokemon Edition

A fork of [DeskGlass](https://github.com/Jondude1/DeskGlass), the frosted-glass desk companion with a self-running village. In this edition, inspired by Taran Matharu's Summoner books, gifted villagers read scrolls at a pentacle circle to summon critters from rifts, bind weakened wild ones, cast glyph spells, train, battle, and keep them as helpers and companions.

It installs next to the original app (separate app id) and updates itself from this repo's releases.

## Updates

Every push to `main` is built by GitHub Actions and published as a GitHub Release. The app checks this public repo's latest release and offers to install it. First install: download the APK from the latest release once and allow installs from your browser.

The signing key in `keystore/` is committed on purpose so each new APK installs over the last one. Do not replace it.
