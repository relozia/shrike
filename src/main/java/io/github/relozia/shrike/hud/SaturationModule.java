package io.github.relozia.shrike.hud;

import io.github.relozia.shrike.Shrike;
import io.github.relozia.shrike.module.Category;
import io.github.relozia.shrike.module.Module;
import io.github.relozia.shrike.setting.Setting;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;

// shows the hidden saturation value on the hunger bar. not a movable widget, it lives on the bar itself
// positions match vanilla's: icon i sits at width/2 + 91 - i*8 - 9, height - 39, and saturation 2 per icon
// issues:
//   - another mod moving the hunger bar (status bar mods) will leave this drawing where vanilla's would be
//   - food exhaustion isn't shown, the server never sends it to the client
public class SaturationModule extends Module {
	private final Setting.Choice mode = add(new Setting.Choice("Mode", "Outline", "Outline", "Above"));
	private final Setting.Color color = add(new Setting.Color("Color", 0xFFF5C518,
		0xFFF5C518, 0xFFE0703A, 0xFFFFFFFF, 0xFF57D163, 0xFF5B8DEF));
	private final Setting.Slider opacity = add(new Setting.Slider("Opacity", 70, 10, 100, 5, "%"));
	private final Setting.Toggle preview = add(new Setting.Toggle("Held Food Preview", true));

	public SaturationModule() {
		super("saturation", "Saturation", Category.HUD, '\ue25b', "Shows your hidden saturation on the hunger bar.");
		HudElementRegistry.attachElementAfter(VanillaHudElements.FOOD_BAR, Shrike.id("saturation"), (g, dt) -> render(g));
	}

	private void render(GuiGraphicsExtractor g) {
		Minecraft mc = Minecraft.getInstance();
		Player p = mc.player;
		// vanilla hides the hunger bar in creative/spectator and while riding something that has hearts
		if (!on() || p == null || mc.gameMode == null || !mc.gameMode.canHurtPlayer()) return;
		if (p.getVehicle() instanceof LivingEntity v && v.showVehicleHealth()) return;

		float sat = p.getFoodData().getSaturationLevel();
		int right = g.guiWidth() / 2 + 91, top = g.guiHeight() - 39;
		int base = ARGB.color(Math.round((float) (opacity.get() / 100.0) * 255), color.get() & 0xFFFFFF);
		draw(g, sat, right, top, base);

		if (preview.on()) {
			FoodProperties food = p.getMainHandItem().get(DataComponents.FOOD);
			if (food != null) {
				// saturation can't go above the food level, so what you'd actually end up with is capped by that
				int foodAfter = Math.min(20, p.getFoodData().getFoodLevel() + food.nutrition());
				float after = Math.min(foodAfter, sat + food.saturation());
				float pulse = 0.35f + 0.35f * (float) Math.sin(Util.getMillis() / 160.0);
				if (after > sat) draw(g, after, right, top, ARGB.multiplyAlpha(base, pulse));
			}
		}
	}

	private void draw(GuiGraphicsExtractor g, float sat, int right, int top, int c) {
		if (mode.get().equals("Above")) {
			int w = Math.round(sat / 20f * 80);
			g.fill(right - w, top - 3, right, top - 1, c);
			return;
		}
		for (int i = 0; i < 10; i++) {
			// quarter steps: each icon holds 2 saturation, so every half point lights one more side
			int q = Mth.clamp((int) ((sat - i * 2) * 2), 0, 4);
			if (q == 0) break;
			int x = right - i * 8 - 9, y = top;
			g.fill(x, y, x + 9, y + 1, c);
			if (q > 1) g.fill(x + 8, y, x + 9, y + 9, c);
			if (q > 2) g.fill(x, y + 8, x + 9, y + 9, c);
			if (q > 3) g.fill(x, y, x + 1, y + 9, c);
		}
	}
}
