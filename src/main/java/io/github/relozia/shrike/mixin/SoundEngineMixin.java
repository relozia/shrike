package io.github.relozia.shrike.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.relozia.shrike.sound.Sounds;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SoundEngine.class)
public class SoundEngineMixin {
	@ModifyReturnValue(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F", at = @At("RETURN"))
	private float shrike$groups(float v, SoundInstance s) {
		return Sounds.BetterSounds.volume(s, v);
	}

	@ModifyReturnValue(method = "calculatePitch", at = @At("RETURN"))
	private float shrike$pitch(float v, SoundInstance s) {
		return Sounds.BetterSounds.pitch(s, v);
	}
}
