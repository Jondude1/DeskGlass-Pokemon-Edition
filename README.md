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


## Following a villager (RPG view)
Tap a villager and the camera closes in on them. A HUD shows their health, food, energy and joy, a quest line for what they are doing, an adventure log and a dialogue box with what they say and think. The village AI still runs them. Tap them again to let go.

## Enemies and the endgame
From the Hut Age, raiders come for the stores; later ogres, rift wraiths and rift drakes follow. Drakes drop a rift-born legend that a summoner can bind. Finish the legacy goals (raids repelled, an ogre slain, City Age, 20 villagers, barracks and library, a drake slain, a legend summoned, a critter at level 40) to begin the Golden Age.

## Speaking to the village
Type an order in the "Speak to the village" box (attack, defend, hide, gather, build, hunt, fish, trade, craft, feast, dance, study, train, explore, rest and many more words). Villagers follow it for a few minutes, with or without a local model.

## Romance and rivalry
Adults who talk a lot grow close, court and are married at the hall or campfire (flower arch, petals, banner). Couples have the babies, mourn when one dies, can become best friends with others, and sometimes fall out in feuds that end in a handshake. Friendships now survive saves.
