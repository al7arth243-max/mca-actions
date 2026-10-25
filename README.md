# MCA Actions (addon for Minecraft Comes Alive Reborn, Forge 1.20.1)

Adds an **Actions** button to the villager Interact page with two scenes:

* **Hug** - you and the villager step together and hug (sway, squeeze, head resting on a shoulder).
* **Cuddle in bed** - finds a bed within 16 blocks (a double bed is preferred, otherwise a single bed) and
  you both lie down together in the middle of it, one on their back with an arm around the other, the other
  curled onto their side with an arm across their chest (based on a reference photo).

While a scene plays the camera goes third person and two buttons appear at the top of the screen:
**Leave** (also Esc) and **Switch** (swap places/roles with the villager).

## Build
Requires JDK 17. The Gradle wrapper is included.

1. Copy the MCA Reborn jar (Forge 1.20.1) to `libs/mca-7.7.1.jar` (rename it exactly; it is git-ignored).
2. `./gradlew build` (Windows: `gradlew.bat build`) -> `build/libs/mca_actions-0.1.0.jar`.
   Put it in `mods/` next to MCA.
3. Test in a dev client with `./gradlew runClient`.

## How it works
1. **Walk up.** The villager paths to its spot and your character is auto-walked there by the client (normal
   movement and walk animation; sneak cancels, it gives up after 10 s). Hug: you walk towards each other.
   Cuddle: you each walk to opposite sides of the bed.
2. **Play.** The server holds both exactly on a pivot (bed centre / midpoint) and the clients render each character
   at an offset from it, so they can overlap in one bed or one hug instead of using vanilla slots.
   Cuddle: each of you turns round, sits on the edge, lies back across the bed, then shuffles round into place
   (about 2.8 s, buttons greyed out meanwhile). Then it loops with breathing and small movements.
3. **Leave** plays the climb-in backwards and puts you both on your feet beside the bed. **Switch** swaps roles
   (who is on their back / who is curled up; who hugs high / low) with an arc so they don't clip through each other.

Scene files: `assets/mca_actions/scenes/<id>.json` (resource packs can override them). Two roles (0 = you by default,
1 = the villager). `roles` loop over `length` seconds with a smooth cyclic curve; `enter` (optional) is a one-shot
sequence played first and in reverse on Leave; `intro`/`outro` are blend times in seconds. Units: positions in 1/16
block in the scene frame (+z = towards the foot of the bed, y up, bed top = 0, floor = -9); `root_rotation` =
[pitch, roll, yaw] degrees; bones use vanilla model part angles in degrees. The first key of `enter` must be the role's
standing spot, which `ServerScenes.rest(...)` also uses, and `ActionType.enterTicks/outroTicks` must match the JSON.

GeckoLib is not used: it needs entities rendered with GeckoLib models, and MCA villagers / the player use vanilla models.

* Rendering: `RenderLivingEvent.Pre` moves/rotates the whole body; one Mixin on vanilla `HumanoidModel.setupAnim`
  (TAIL) sets the limbs afterwards.
* MCA's menu pages are JSON; `assets/mca/api/gui/actions.json` is the new page and the Actions button is appended to
  MCA's loaded Interact page at runtime (ClientEvents), so no MCA file is replaced.

## Adding a scene
1. Add an entry to `ActionType`, start it from `ServerScenes.request(...)` (pick a pivot + scene yaw).
2. Add its `rest(...)` spots in `ServerScenes`, `scenes/<id>.json` and a button in `assets/mca/api/gui/actions.json` + lang key `gui.button.mca_actions.<id>`.
