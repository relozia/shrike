package io.github.relozia.kestrel.module;

import io.github.relozia.kestrel.camera.CameraTweaks;
import io.github.relozia.kestrel.chat.ChatBubbles;
import io.github.relozia.kestrel.chat.ChatModules;
import io.github.relozia.kestrel.chat.Players;
import io.github.relozia.kestrel.camera.Freelook;
import io.github.relozia.kestrel.camera.Zoom;
import io.github.relozia.kestrel.hud.ArmorHudWidget;
import io.github.relozia.kestrel.nav.Nav;
import io.github.relozia.kestrel.pvp.Coaches;
import io.github.relozia.kestrel.pvp.KillEffects;
import io.github.relozia.kestrel.pvp.PvpTools;
import io.github.relozia.kestrel.qol.Qol;
import io.github.relozia.kestrel.sound.Sounds;
import io.github.relozia.kestrel.pvp.PvpVisuals;
import io.github.relozia.kestrel.hud.ClockWidget;
import io.github.relozia.kestrel.hud.CooldownHudWidget;
import io.github.relozia.kestrel.hud.CoordsWidget;
import io.github.relozia.kestrel.hud.EffectsWidget;
import io.github.relozia.kestrel.hud.FpsWidget;
import io.github.relozia.kestrel.hud.ItemTrackerWidget;
import io.github.relozia.kestrel.hud.KeystrokesWidget;
import io.github.relozia.kestrel.hud.MoreWidgets;
import io.github.relozia.kestrel.hud.SaturationModule;
import io.github.relozia.kestrel.hud.SimpleWidgets;
import io.github.relozia.kestrel.hud.VanillaWidgets;
import io.github.relozia.kestrel.hud.WatermarkWidget;
import io.github.relozia.kestrel.visual.Animations;
import io.github.relozia.kestrel.visual.Cosmetics;
import io.github.relozia.kestrel.visual.Crosshair;
import io.github.relozia.kestrel.visual.Fireflies;
import io.github.relozia.kestrel.visual.ScreenTweaks;
import io.github.relozia.kestrel.visual.WorldVisuals;
import io.github.relozia.kestrel.visual.WorldTweaks;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Modules {
	private static final List<Module> ALL = new ArrayList<>();

	// not in ALL on purpose, it's reached from the gear in the menu header, not a card
	public static final KestrelSettings SETTINGS = new KestrelSettings();

	private Modules() {}

	public static void init() {
		// card order in the menu, roughly most used first
		ALL.add(new FpsWidget());
		ALL.add(new SimpleWidgets.Cps());
		ALL.add(new KeystrokesWidget());
		ALL.add(new ArmorHudWidget());
		ALL.add(new EffectsWidget());
		ALL.add(new ItemTrackerWidget());
		ALL.add(new SaturationModule());
		ALL.add(new CoordsWidget());
		ALL.add(new SimpleWidgets.Ping());
		ALL.add(new SimpleWidgets.Tps());
		ALL.add(new SimpleWidgets.Bps());
		ALL.add(new SimpleWidgets.Memory());
		ALL.add(new SimpleWidgets.Server());
		ALL.add(new ClockWidget());
		ALL.add(new SimpleWidgets.Session());
		ALL.add(new SimpleWidgets.Stopwatch());
		ALL.add(new SimpleWidgets.Combo());
		ALL.add(new SimpleWidgets.Reach());
		ALL.add(new WatermarkWidget());
		ALL.add(new VanillaWidgets.Scoreboard());
		ALL.add(new VanillaWidgets.BossBars());
		ALL.add(new VanillaWidgets.Title());
		ALL.add(new VanillaWidgets.ActionBar());
		ALL.add(new Crosshair());
		ALL.add(new ScreenTweaks.Hotbar());

		ALL.add(new Zoom());
		ALL.add(new Freelook());
		ALL.add(new CameraTweaks.Fov());
		ALL.add(new CameraTweaks.Bobbing());
		ALL.add(new CameraTweaks.HurtCamera());
		ALL.add(new CameraTweaks.AspectRatio());

		ALL.add(new WorldTweaks.Fullbright());
		ALL.add(new WorldTweaks.BlockOverlay());
		ALL.add(new WorldTweaks.FireHeight());
		ALL.add(new ScreenTweaks.Title());
		ALL.add(new Cosmetics());
		ALL.add(new ScreenTweaks.Background());
		ALL.add(new ScreenTweaks.Tooltips());

		ALL.add(new Animations());

		ALL.add(new ChatModules.Chat());
		ALL.add(new ChatModules.Heads());
		ALL.add(new ChatModules.Mentions());
		ALL.add(new ChatModules.HidePassword());
		ALL.add(new ChatModules.AutoGG());
		ALL.add(new ChatBubbles());
		ALL.add(new Players.NameTags());
		ALL.add(new Players.BetterTab());
		ALL.add(new Players.Friends());

		ALL.add(new PvpVisuals.Hitboxes());
		ALL.add(new PvpVisuals.HitColor());
		ALL.add(new PvpVisuals.DamageNumbers());
		ALL.add(new KillEffects());
		ALL.add(new PvpVisuals.TntTimer());
		ALL.add(new PvpTools.LowHealth());
		ALL.add(new Coaches.JumpReset());
		ALL.add(new Coaches.SwapHelper());
		ALL.add(new PvpTools.Totems());
		ALL.add(new PvpTools.PvpInfo());
		ALL.add(new PvpTools.Cooldowns());
		ALL.add(new CooldownHudWidget());
		ALL.add(new PvpTools.HealHighlight());
		ALL.add(new PvpTools.LockSlot());
		ALL.add(new PvpTools.DisconnectGuard());

		ALL.add(new WorldVisuals.Sky());
		ALL.add(new WorldVisuals.Glint());
		ALL.add(new WorldVisuals.DroppedItems());
		ALL.add(new WorldVisuals.HideArmor());
		ALL.add(new WorldVisuals.NoRender());
		ALL.add(new WorldVisuals.MotionBlur());
		ALL.add(new Fireflies());
		ALL.add(new WorldVisuals.Particles());

		ALL.add(new Sounds.KeySounds());
		ALL.add(new Sounds.BetterSounds());
		ALL.add(new Qol.ModernKeybinds());
		ALL.add(new Qol.ShulkerPreview());
		ALL.add(new Qol.PickupTracker());
		ALL.add(new Qol.ScreenshotHelper());
		ALL.add(new Qol.ServerNotes());
		ALL.add(new Qol.ServerProfiles());

		ALL.add(new Nav.Waypoints());
		ALL.add(new Nav.Compass());
		ALL.add(new Nav.AutoWaypoint());
		ALL.add(new Nav.TrapTimer());
		ALL.add(new MoreWidgets.MiniDebug());
		ALL.add(new MoreWidgets.Waila());
		ALL.add(new MoreWidgets.InventoryHud());
		ALL.add(new MoreWidgets.PackDisplay());
		ALL.add(new MoreWidgets.MusicPlayer());
	}

	public static List<Module> all() {
		return Collections.unmodifiableList(ALL);
	}
}
