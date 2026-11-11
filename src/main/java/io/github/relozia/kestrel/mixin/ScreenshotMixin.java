package io.github.relozia.kestrel.mixin;

import io.github.relozia.kestrel.qol.Qol;
import java.util.function.Consumer;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Screenshot.class)
public class ScreenshotMixin {
	// every save path ends here, the "saved as" message comes back through this callback once the file is written
	@ModifyVariable(method = "grab(Ljava/io/File;Ljava/lang/String;Lcom/mojang/blaze3d/pipeline/RenderTarget;ILjava/util/function/Consumer;)V", at = @At("HEAD"), argsOnly = true)
	private static Consumer<Component> kestrel$saved(Consumer<Component> og) {
		return msg -> {
			og.accept(msg);
			Qol.ScreenshotHelper.saved(msg);
		};
	}
}
