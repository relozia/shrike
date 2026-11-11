package io.github.relozia.kestrel.hud;

import com.google.gson.JsonObject;
import io.github.relozia.kestrel.gui.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

// vanilla's own hud parts made movable. vanilla still draws them (live scores, boss colors, title fades),
// the pose just gets moved so the box vanilla was going to use lands where the widget was dragged
// issues:
//   - the box is only known while vanilla draws it, with nothing on screen the editor shows a sample size
//   - once dragged there's no "back to vanilla's spot" besides turning the module off
public abstract class VanillaWidget extends HudWidget {
	private final int sampleW, sampleH;
	private int nx, ny, nw, nh;
	private long seen;
	private boolean pushed, placed;

	protected VanillaWidget(String name, char icon, String desc, float fx, float fy, int sampleW, int sampleH) {
		super(name, icon, desc, fx, fy);
		this.sampleW = sampleW;
		this.sampleH = sampleH;
	}

	@Override protected boolean units() { return false; }
	@Override protected Look look() { return Look.NONE; }

	// push() runs every frame vanilla draws it, so anything older than a few frames means it's gone
	private boolean showing() {
		return Util.getMillis() - seen < 200;
	}

	@Override protected int contentW() { return showing() ? nw : sampleW; }
	@Override protected int contentH() { return showing() ? nh : sampleH; }

	// until it's dragged it sits exactly where vanilla puts it, so turning the module on just for
	// a setting like Hide Numbers doesn't nudge anything
	@Override
	public int px(int sw) {
		return !placed && showing() ? nx : super.px(sw);
	}

	@Override
	public int py(int sh) {
		return !placed && showing() ? ny : super.py(sh);
	}

	@Override
	public void moveTo(float x, float y, int sw, int sh) {
		placed = true;
		super.moveTo(x, y, sw, sh);
	}

	// right before vanilla draws, with the box it's about to use (gui coords, unscaled)
	public void push(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
		if (!on()) return;
		nx = x0;
		ny = y0;
		nw = x1 - x0;
		nh = y1 - y0;
		seen = Util.getMillis();
		if (!placed) return;
		pushed = true;
		g.pose().pushMatrix();
		g.pose().translate(px(g.guiWidth()), py(g.guiHeight()));
		g.pose().scale(scale, scale);
		g.pose().translate(-x0, -y0);
	}

	public void pop(GuiGraphicsExtractor g) {
		if (!pushed) return;
		pushed = false;
		g.pose().popMatrix();
	}

	// only the editor's stand in, the real thing is vanilla's
	@Override
	public void render(GuiGraphicsExtractor g, int sw, int sh) {
		if (editing && !showing()) super.render(g, sw, sh);
	}

	@Override
	protected void content(GuiGraphicsExtractor g) {
		g.fill(0, 0, sampleW, sampleH, 0x50000000);
		Component c = Ui.t(name);
		Ui.text(g, c, (sampleW - Ui.width(c)) / 2, (sampleH - 8) / 2, 0xFFBBBBBB);
	}

	@Override
	public JsonObject save() {
		JsonObject o = super.save();
		o.getAsJsonObject("hud").addProperty("placed", placed);
		return o;
	}

	@Override
	public void load(JsonObject o) {
		super.load(o);
		JsonObject p = o.getAsJsonObject("hud");
		placed = p != null && p.has("placed") && p.get("placed").getAsBoolean();
	}
}
