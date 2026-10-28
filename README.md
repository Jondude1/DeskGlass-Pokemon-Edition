# Summoner's Village

A fork of [DeskGlass](https://github.com/Jondude1/DeskGlass), the frosted-glass desk companion with a self-running village. In this edition, inspired by Taran Matharu's Summoner books, gifted villagers read scrolls at a rune-carved rift gate to call critters through, bind weakened wild ones with rune chains, cast glyph spells, train, battle, and keep them as helpers and companions.

It installs next to the original app (separate app id) and updates itself from this repo's releases.

## Updates

Every push to `main` is built by GitHub Actions and published as a GitHub Release. The app checks this public repo's latest release and offers to install it. First install: download the APK from the latest release once and allow installs from your browser.

The signing key in `keystore/` is committed on purpose so each new APK installs over the last one. Do not replace it.

## How summoning works here

- **The gift** runs in families. Children carry it unseen until they come of age, then stand before the rift gate for the **gift test**. Gifted young summoners get a **mentor** and take lessons.
- **Scrolls** come in three tiers (common, fine, rare), copied out at the gate by villagers (gifted or not) from hides, wood and metal. Better scrolls call rarer lines and need a higher summoner level.
- **Trust** grows while a critter is out with its summoner (and slowly while it rests in the pack), and from lessons and wins. Trust is the evolution milestone: 18 for a first evolution, 40 for a second. A deep bond also lowers resist chances when summoning.
- **Evolution** is a rite: once trust is deep enough, the summoner must be beside the critter and spend mana.
