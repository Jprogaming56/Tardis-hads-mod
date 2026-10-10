# Renaissance Sign

Personal add-on for the Adventures in Time (AiT) Forge port on Minecraft 1.20.1. Personal use only, not for sharing.

AiT's Renaissance police box has a blank sign band. This adds the sign from the show: large **POLICE**, the small
stacked **PUBLIC / CALL**, and large **BOX**, as warm cream lettering on a dark inset panel, on all four sides.
The lettering glows (it is in the emission texture), so it stays lit at night like the prop.

## How it works

- `SignGeometry` builds a new sign band in the same place as AiT's own (`PCB_t`, under the stepped roof), one unit
  taller, with a dark inset panel and the lettering built from small blocks.
- `SignPatcher` watches AiT's exterior renderers a few times a second. Whenever one holds a Renaissance box model that
  has not been patched yet, it swaps the sign part inside that model for the new one. AiT is only touched through
  reflection, so this compiles without AiT on the classpath, like HADS Switch.
- The textures in `assets/ait/textures/blockentities/exteriors/police_box/` are AiT's four Renaissance textures
  (normal, emission, snowy, chorus) with three flat colour patches added in an unused corner. This mod loads after AiT,
  so these copies win. The sign blocks sample those patches.

## Build

Push this folder to a GitHub repo. The Actions workflow builds `renaissance-sign-1.0.0.jar`; put it in `mods` next to
the AiT Forger jar.

## Known limits

- The sign is added to each Renaissance model about a quarter of a second after it is created, so a freshly placed or
  re-skinned box can show a blank sign for a few frames.
- Places where AiT builds a separate throwaway model (for example the view through the open door) can still show the
  blank sign.
- If another mod replaces AiT's Renaissance textures and loads later, its textures will win and the lettering patches
  will be missing.
- Check `logs/latest.log` for `Renaissance Sign:` lines. "new sign installed" means it worked; a warning means AiT's
  layout differs from what this expects.
