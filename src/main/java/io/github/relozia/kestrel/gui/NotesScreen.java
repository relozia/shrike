package io.github.relozia.kestrel.gui;

import io.github.relozia.kestrel.qol.Qol;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

// a note for one server from the server list. vanilla's multi line box, saved on Done (esc throws it away)
public class NotesScreen extends Screen {
	private final Screen parent;
	private final String ip, name;
	private MultiLineEditBox box;

	public NotesScreen(Screen parent, String ip, String name) {
		super(Component.literal("Notes"));
		this.parent = parent;
		this.ip = ip;
		this.name = name;
	}

	@Override
	protected void init() {
		int w = Math.min(width - 40, 320), h = Math.min(height - 90, 160), x = (width - w) / 2, y = 42;
		box = MultiLineEditBox.builder().setX(x).setY(y).setPlaceholder(Component.literal("Anything you want to remember about this server"))
			.build(font, w, h, Component.literal("Note"));
		box.setCharacterLimit(2000);
		box.setValue(Qol.note(ip));
		addRenderableWidget(box);
		setInitialFocus(box);
		addRenderableWidget(Button.builder(Component.literal("Done"), b -> {
			Qol.note(ip, box.getValue().strip());
			minecraft.gui.setScreen(parent);
		}).bounds(width / 2 - 102, y + h + 10, 100, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> minecraft.gui.setScreen(parent)).bounds(width / 2 + 2, y + h + 10, 100, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
		super.extractRenderState(g, mx, my, a);
		Component t = Ui.bold("Notes for " + name);
		Ui.text(g, t, (width - Ui.width(t)) / 2, 22, Ui.TEXT);
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		if (e.isEscape()) {
			minecraft.gui.setScreen(parent);
			return true;
		}
		return super.keyPressed(e);
	}
}
