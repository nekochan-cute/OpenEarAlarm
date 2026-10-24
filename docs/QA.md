# On-device test checklist

Emulators cover scheduling, the UI and the playback service. These checks need real phones and headphones. When something fails, note the phone model, Android version, app commit and the alarm's settings, and attach the history from Settings → 履歴.

## Timing

- [ ] An alarm 2 minutes ahead rings on time with the screen locked (repeat on Android 9 and Android 16).
- [ ] 20 consecutive alarms in Doze ring on time without the app open.
- [ ] Weekly alarms move correctly across a time-zone change and a daylight-saving change.

## Output and volume

- [ ] Wired, USB-C, Bluetooth A2DP and LE Audio headphones: the alarm plays in the headphones and the ringing screen says イヤホン.
- [ ] Unplugging or disconnecting during ringing: sound continues from the speaker and the screen switches to スピーカー.
- [ ] Reconnecting during ringing: no double playback and no sudden jump in volume.
- [ ] With 音量を指定する on, the speaker and headphone levels match the sliders, including after a disconnect.
- [ ] After stopping, the connected output returns to its previous level. A level changed during ringing is kept.
- [ ] Alarm stream and media stream, fade-in 0, 10 and 60 s.

## Stopping and snoozing

- [ ] Stop and snooze from the ringing screen and from the notification, locked and unlocked.
- [ ] The snooze limit hides snooze once it is used up. Automatic stop ends the alarm at the set time.
- [ ] Two alarms at the same time ring once and both stop together.

## Restarts

- [ ] Reboot without unlocking: the next alarm and a pending snooze ring.
- [ ] App update keeps all alarms scheduled.
- [ ] After a force stop the alarms ring again once the app has been opened.

## Permissions and settings

- [ ] With notifications off, the home screen shows the warning and 許可する opens the permission prompt or the notification settings.
- [ ] With full-screen alarms off (Android 14+), the warning appears and 許可する opens the system setting.
- [ ] Do Not Disturb with alarms allowed and with alarms blocked.

## Display

- [ ] 320 dp wide screens, landscape, and font scale 1.0, 1.5 and 2.0: nothing important is cut off and the weekday toggles stay on one line.
- [ ] TalkBack reads the alarm rows, switches, weekday toggles and ringing buttons in order.
