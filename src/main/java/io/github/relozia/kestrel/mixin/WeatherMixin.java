package io.github.relozia.kestrel.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.relozia.kestrel.visual.WorldVisuals;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Level is shared with the integrated server, only the client's copy gets the fake weather
@Mixin(Level.class)
public class WeatherMixin {
	@ModifyReturnValue(method = "getRainLevel", at = @At("RETURN"))
	private float kestrel$rain(float v) {
		return (Object) this instanceof ClientLevel ? WorldVisuals.Sky.rain(v) : v;
	}

	@ModifyReturnValue(method = "getThunderLevel", at = @At("RETURN"))
	private float kestrel$thunder(float v) {
		return (Object) this instanceof ClientLevel ? WorldVisuals.Sky.thunder(v) : v;
	}
}
