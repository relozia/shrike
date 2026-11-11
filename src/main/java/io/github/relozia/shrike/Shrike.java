package io.github.relozia.shrike;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.relozia.shrike.config.Blacklist;
import io.github.relozia.shrike.config.Config;
import io.github.relozia.shrike.config.Profiles;
import io.github.relozia.shrike.gui.ShrikeScreen;
import io.github.relozia.shrike.hud.Hud;
import io.github.relozia.shrike.module.Module;
import io.github.relozia.shrike.module.Modules;
import io.github.relozia.shrike.pvp.PvpTools;
import io.github.relozia.shrike.pvp.PvpVisuals;
import io.github.relozia.shrike.track.Combat;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Shrike implements ClientModInitializer {
	public static final String ID = "shrike";
	public static final Logger LOG = LoggerFactory.getLogger(ID);

	public static KeyMapping menuKey;

	@Override
	public void onInitializeClient() {
		Modules.init();
		// has to run first, it decides which file Config.load reads
		Profiles.init();
		Config.load();
		Blacklist.init();
		// only on join and on leaving, never mid session. mc.execute so it lands on the client thread
		ClientPlayConnectionEvents.JOIN.register((h, s, mc) -> mc.execute(Blacklist::joined));
		ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> mc.execute(Blacklist::left));
		// a fresh install has no config to load, modules that start on still need their enable()
		for (Module m : Modules.all()) m.sync();

		KeyMapping.Category cat = KeyMapping.Category.register(id("main"));
		menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.shrike.menu", InputConstants.KEY_RSHIFT, cat));

		HudElementRegistry.addLast(id("hud"), Hud::render);
		Combat.init();
		PvpVisuals.init();
		PvpTools.init();

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			// only from in game, opening on top of chat or an inventory would eat whatever you were typing
			while (menuKey.consumeClick()) {
				if (mc.gui.screen() == null) mc.gui.setScreen(new ShrikeScreen());
			}
			for (Module m : Modules.all()) {
				m.keys(mc);
				if (m.on()) m.tick(mc);
			}
			Config.tick();
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> Config.save());

		LOG.info("shrike up, {} module(s)", Modules.all().size());
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(ID, path);
	}
}
