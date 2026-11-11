package io.github.relozia.shrike.mixin;

import java.util.List;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EditBox.class)
public interface EditBoxAccessor {
	@Accessor("formatters")
	List<EditBox.TextFormatter> shrike$formatters();
}
