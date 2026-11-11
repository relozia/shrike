package io.github.relozia.shrike.mixin;

import io.github.relozia.shrike.chat.ChatAccess;
import io.github.relozia.shrike.chat.ChatButton;
import io.github.relozia.shrike.chat.ChatModules;
import io.github.relozia.shrike.gui.Ui;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// ctrl+f turns the chat input into a search box (enter doesn't send, esc leaves search first),
// right click on a line copies it
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {
	@Shadow protected EditBox input;

	@Unique private boolean shrike$searching;
	@Unique private String shrike$draft = "";
	@Unique private final List<Button> shrike$tabs = new ArrayList<>();
	@Unique private @Nullable Button shrike$searchButton;

	protected ChatScreenMixin(Component title) {
		super(title);
	}

	@Unique
	private ChatAccess shrike$chat() {
		return (ChatAccess) minecraft.gui.hud.getChat();
	}

	// the tab row over the input: All, PMs, Server, Mentions with unread counts, then Search
	@Inject(method = "init", at = @At("TAIL"))
	private void shrike$tabsInit(CallbackInfo ci) {
		shrike$tabs.clear();
		shrike$searchButton = null;
		if (!ChatModules.Chat.tabbed() && !ChatModules.Chat.searches()) return;
		int x = 2;
		if (ChatModules.Chat.tabbed()) {
			for (int i = 0; i < ChatModules.Chat.TABS.length; i++) {
				int t = i;
				int w = font.width(ChatModules.Chat.TABS[i] + " (99)") + 8;
				Button b = new ChatButton(x, height - 27, w, 12, Component.literal(ChatModules.Chat.TABS[i]), btn -> {
					ChatModules.Chat.pick(t);
					shrike$chat().shrike$refresh();
					// a click focuses the button, give it back to the input so enter sends chat
					setFocused(input);
				});
				shrike$tabs.add(addRenderableWidget(b));
				x += w + 2;
			}
		}
		if (ChatModules.Chat.searches()) {
			shrike$searchButton = addRenderableWidget(new ChatButton(x, height - 27, font.width("Search (Ctrl+F)") + 8, 12, Component.literal("Search (Ctrl+F)"), btn -> {
				shrike$toggleSearch();
				setFocused(input);
			}));
		}
	}

	@Inject(method = "extractRenderState", at = @At("HEAD"))
	private void shrike$tabLabels(GuiGraphicsExtractor g, int mx, int my, float a, CallbackInfo ci) {
		for (int i = 0; i < shrike$tabs.size(); i++) {
			int n = ChatModules.Chat.UNREAD[i];
			MutableComponent c = Component.literal(ChatModules.Chat.TABS[i] + (n > 0 ? " (" + n + ")" : ""));
			if (i == ChatModules.Chat.tab) c.withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);
			shrike$tabs.get(i).setMessage(c);
		}
		if (shrike$searchButton != null) {
			shrike$searchButton.setMessage(shrike$searching ? Component.literal("Searching... (Esc)").withStyle(ChatFormatting.YELLOW) : Component.literal("Search (Ctrl+F)"));
		}
	}

	@Unique
	private void shrike$toggleSearch() {
		if (shrike$searching) {
			shrike$stop();
		} else {
			shrike$searching = true;
			shrike$draft = input.getValue();
			input.setValue("");
		}
	}

	@Unique
	private void shrike$stop() {
		shrike$searching = false;
		ChatModules.Chat.search(null);
		shrike$chat().shrike$refresh();
		input.setValue(shrike$draft);
	}

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void shrike$keys(KeyEvent e, CallbackInfoReturnable<Boolean> cir) {
		// the held key state, not the event modifiers (cmd on mac comes through here too)
		if (e.key() == GLFW.GLFW_KEY_F && minecraft.hasControlDown() && ChatModules.Chat.searches()) {
			shrike$toggleSearch();
			cir.setReturnValue(true);
			return;
		}
		if (!shrike$searching) return;
		if (e.isEscape()) {
			shrike$stop();
			cir.setReturnValue(true);
		} else if (e.isConfirmation()) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "onEdited", at = @At("TAIL"))
	private void shrike$query(String value, CallbackInfo ci) {
		if (!shrike$searching) return;
		ChatModules.Chat.search(value);
		shrike$chat().shrike$refresh();
	}

	// closing chat mid search shouldn't leave the chat filtered, and the tab goes back to All if you want
	@Inject(method = "removed", at = @At("HEAD"))
	private void shrike$closed(CallbackInfo ci) {
		if (shrike$searching) shrike$stop();
		if (ChatModules.Chat.backToAll() && ChatModules.Chat.tab != 0) {
			ChatModules.Chat.pick(0);
			shrike$chat().shrike$refresh();
		}
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void shrike$copy(MouseButtonEvent e, boolean dbl, CallbackInfoReturnable<Boolean> cir) {
		if (e.button() != 1 || !ChatModules.Chat.copies()) return;
		GuiMessage m = shrike$chat().shrike$at(e.x(), e.y(), height);
		if (m == null) return;
		// shift copies the colors too, as & codes, so it can be pasted into a sign or a plugin's chat format
		boolean colors = minecraft.hasShiftDown();
		Component shown = m.content();
		String text = colors ? ChatModules.codes(shown) : shown.getString();
		if (!ChatModules.Chat.copyStamp()) text = ChatModules.unstamp(text);
		minecraft.keyboardHandler.setClipboard(text);
		minecraft.gui.hud.setOverlayMessage(Component.literal(colors ? "Copied message with color codes" : "Copied message"), false);
		Ui.sound("click");
		cir.setReturnValue(true);
	}

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void shrike$searchHint(GuiGraphicsExtractor g, int mx, int my, float a, CallbackInfo ci) {
		if (shrike$searching && input.getValue().isEmpty()) {
			g.text(font, Component.literal("Type to search chat...").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), 4, height - 12, 0xFFFFFFFF);
		}
	}
}
