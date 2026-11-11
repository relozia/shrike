# Kestrel

A mod for 26.2, I'l later change it into a client and add newer versions. Right Shift to open the the gui. ONLY 26.2

## Features

Approximately 90 modules. The menu has a search bar so it is quicker to search for what you want.

**HUD** - FPS, CPS, Ping, TPS, Coordinates, Armor Durability, Potion Effects, Keystrokes, Item Tracker, Cooldowns, Music Player, and a few others. There is also the vanilla Scoreboard, Boss Bars, Titles and Action Bar. All can be moved via the move hud menu

**Visual** - 1.7 Animations, Crosshair Editor, Sky & Lighting changes, Glint Colors, Fireflies, Dropped Item Changes, Hotbar, Capes and Name Badge.

**Combat** - Hitboxes, Hit Color, Damage Numbers, TNT Timer, Cooldown Display, Reach Tracker

**Chat** - Timestamps, Tabs, Mentions, Chat Heads, Bubbles, Friends, Name Tags and Improved Tab List.

**Utility** - Waypoints, Compass, Server Notes & Profiles, Shulker Preview, Screenshot Helper, Modern Keybinds.

## Configuration

`config/kestrel/capes` - To add a cape png using the vanilla cape format.

`config/kestrel/soundpacks` - To add key sound packs

These are only loaded once when starting the game so you have to restart to add them

## Windows Only

There are 3 features that will request something from Windows, they run a PowerShell command and won't work on another os cuz im lazy

- Music Player Widget - Gets the Volume flyout.
- Desktop Notification - When mentioned whilst tabbed out.
- Screenshot to Clipboard.

These are disabled by default. They will do nothing on Linux/macOS. The script used for the Music Player is inside the jar in `assets/kestrel/media/media.ps1`. You can read the source

## Issues

- Capes and Name Badge are only visible to you. No server side code has been added.
- Any new cape png or sound pack folder will not show until you restart.

## Credits

Fonts, Icons and Sounds. These are all done by other people. They can be found in [CREDITS.md](CREDITS.md) along with their licenses. The license texts are included in the jar.

## License

GPL-3.0-or-later
