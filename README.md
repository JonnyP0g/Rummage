# Rummage

A Fabric mod for Minecraft 26.3. Press **R**, type an item, and every loaded chest, trapped chest, barrel and
shulker box holding it is highlighted through walls.

## What it searches

- Item id and normal name, so `diamond sword` finds every diamond sword, renamed or not.
- Custom (anvil / name tag) names, so an item renamed "Excalibur" is found by `excalibur` too.
- Everything inside shulker boxes that sit in a container.
- Enchantments stored on enchanted books, e.g. `mending` or `sharpness v`.

Multiple words are AND-ed: `book mending` finds enchanted books with Mending.

## How it works

Chest contents are not sent to clients until you open them, so Rummage has a small server half. Install the
same jar on the server and on your client. When you open the search screen the client asks the server to scan
the chunks it already has loaded around you; the server streams back a compact summary and the client does all
the searching locally, so results update as you type.

## Usage

- **R** (rebindable under Controls > Rummage): open the search bar.
- **Enter** or the Highlight button: highlight matches for 60 seconds and close the screen.
- Search for nothing and press Enter to clear the highlights.

## Building

Requires JDK 25. Run `./gradlew build`; the jar is in `build/libs/`. `./gradlew runClient` starts a dev client.

## License

CC0-1.0 (see LICENSE).
