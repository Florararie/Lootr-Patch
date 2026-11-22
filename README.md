# Lootr Polymer Patch

A server-side [Polymer](https://modrinth.com/mod/polymer) patch for [Lootr](https://modrinth.com/mod/lootr) 1.23.x on Minecraft 26.1.2. Players without the mod, Polymer or a resource pack can see, place and open Lootr's loot containers. Only the server needs anything installed.

## Install

Server needs Java 25, Fabric Loader 0.19.5+, Fabric API, Polymer 0.16.x and Lootr 1.23.x.

Clients need nothing. Polymer builds the pack from the jar's own assets; enable AutoHost (`config/polymer/auto-host.json`, `"enabled": true`) and restart, or run `/polymer generate-pack` and host the zip yourself. Without the pack the chests still work but render as exposed copper chests.

## Limitations

- Lootr's chest minecart is drawn with vanilla's chest, because the chest is part of the client's own cart model; the cart does not wear Lootr's chest art.
- Vanilla single exposed copper chests look like Lootr's exposed copper chest while the pack is loaded, because their sprite is the one blanked for the carrier. This is done instead of using a barrier as a donor to retain the same hitbox as chests as opposed to a full block.
- Lootr's decorated pots are shown as vanilla decorated pots, so they wear vanilla's terracotta rather than Lootr's gold rim.

## Building

```
gradle build
```

The jar lands in `build/libs/`. Lootr has no public maven repository, so the build takes the jar named by `lootr_file` in `gradle.properties` (by default from the sibling dev instance's `run/mods`).
