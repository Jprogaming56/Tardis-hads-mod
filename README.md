# Tardis-hads-mod
Adds the HADS as a block that acts as a switch to turn it on or off.

## Linking to the alarm button
Place a HADS Switch block inside a TARDIS and it links to that TARDIS's console alarm button
(the `ait:alarms` control, right or left click):

- The alarm button now toggles HADS instead of the alarm.
- Every HADS Switch in that TARDIS lights up or goes dark to match, whichever way HADS was toggled.
- If the alarm is already ringing, pressing the button still switches the alarm off first
  (config `alarmButtonSilencesRingingAlarm`, default `true`; set it to `false` to always toggle HADS).
- Break the last HADS Switch in the TARDIS and the button goes back to being the normal alarm.
- AiT's own tools (repair tool, control editor, redstone control block) still work on the button.
