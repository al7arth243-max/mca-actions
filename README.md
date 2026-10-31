# MCA Actions (Forge 1.20.1)

An add-on for **MCA Reborn** that adds an **Actions** button to the villager interaction menu.

## Requirements
- Minecraft 1.20.1, Forge 47.x
- MCA Reborn 7.7.x for 1.20.1 (built against `7.7.1-beta.4`)
- GeckoLib 4.4.x for Forge 1.20.1 (4.4.9 recommended)

## Actions
| Action | Requirements | What happens | Hearts |
|---|---|---|---|
| **Hug** | none | You and the villager hug for 3 seconds, with a reach-in, a squeeze and a little sway | +4 (once per minute per villager) |
| **Cuddle in bed** | villager is an adult, and there's a free **double bed** (two beds side by side, facing the same way) within 8 blocks | You lie on your back with an arm around them. They lie on their side, head on your shoulder, with an arm across your chest that you hold. | +1 every 10 seconds, up to +10 per cuddle |

While cuddling, a **Stop** button appears where the menu buttons were. Pressing **Shift**, taking damage, moving away or breaking a bed also ends it.
The camera switches to third person during an action and goes back afterwards.

## How it works
- **Menu:** MCA builds its menus from JSON files. A mixin adds the **Actions** button to the main page. The actions page is `assets/mcaactions/api/gui/mcaactions_actions.json`, and MCA's own back arrow returns to the main page.
- **Animations:** while an action plays, the player and the villager are drawn with a GeckoLib model that bends at the elbows, knees and waist. That model wears their real skin: MCA's combined villager skin, or the player's own skin, slim or wide.
- **Files:** the models are `assets/mcaactions/geo/humanoid_*.geo.json` and the animations are `assets/mcaactions/animations/actions.animation.json`. Both can be opened and edited in Blockbench.
- **Villager behaviour:** the villager's AI is paused during an action and always restored afterwards, even if the game closes mid-action.

## Building
Every push to GitHub builds the jar automatically. Go to **Actions → latest Build run → Artifacts → mca-actions**.
To build locally, run `gradlew build`. The jar goes to `build/libs/`.

`libs/mca-forge-7.7.1-beta.4.jar` holds MCA's Forge classes. It's only used for compiling and is **not** bundled into the add-on. Players still need MCA installed. MCA Reborn is GPL-3.0, and so is this add-on.

## Adding more actions
1. Add a button to `mcaactions_actions.json` with the identifier `gui.button.mcaactions.do.<name>`, plus a matching line in `lang/en_us.json`.
2. Handle `<name>` in `ActionManager.start(...)`.
3. Add its animations to `actions.animation.json`.
