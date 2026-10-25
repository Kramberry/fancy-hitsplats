package com.fancyhitsplats;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

class FancyHitsplatsOverlay extends Overlay
{
	// Head height offset (local units above the model's logical height).
	private static final int HEAD_OFFSET = 40;

	// The game's splat slot offsets in pixels: centre, above, left, right.
	private static final int[][] SLOT_OFFSETS = {{0, 0}, {0, -20}, {-15, -10}, {15, -10}};

	// NPCs whose definition overrides the height the game draws hitsplats (and health bars) at.
	// The client API doesn't expose that field, so it's read from the game's NPC definitions;
	// without it, splats on these NPCs would be drawn too low.
	private static final int[][] NPC_SPLAT_HEIGHTS = {
		{NpcID.YAMA_THRONE, 470},
		{NpcID.BLACK_DEMON_STRONGHOLDCAVE_1, 459},
		{NpcID.SARIM_SEAGULL_PIER, 50},
		{NpcID.SARIM_SEAGULL_PIER_BIG, 50},
		{NpcID.GREATER_DEMON_STRONGHOLDCAVE_1, 380},
		{NpcID.GREATER_DEMON_STRONGHOLDCAVE_2, 380},
		{NpcID.GREATER_DEMON_STRONGHOLDCAVE_3, 380},
		{NpcID.BLACK_DEMON_STRONGHOLDCAVE_2, 459},
		{NpcID.BLACK_DEMON_STRONGHOLDCAVE_3, 459},
		{NpcID.BLACK_DEMON_STRONGHOLDCAVE_5, 459},
		{NpcID.LESSER_DEMON_SLAYERCAVE_1, 340},
		{NpcID.LESSER_DEMON_SLAYERCAVE_2, 340},
		{NpcID.LESSER_DEMON_SLAYERCAVE_3, 340},
		{NpcID.EARTH_SURGE_RAISED, 350},
		{NpcID.WATER_SURGE_RAISED, 350},
		{NpcID.FIRE_SURGE_RAISED, 350},
		{NpcID.WIND_SURGE_RAISED, 350},
		{NpcID.WGS_BALANCE_ELEMENTAL, 365},
		{NpcID.WGS_BALANCE_ELEMENTAL_DYING, 365},
		{NpcID.WGS_BALANCE_ELEMENTAL_CUTSCENE, 365},
		{NpcID.TORMENTED_DEMON_1, 460},
		{NpcID.TORMENTED_DEMON_2, 460},
		{NpcID.TORMENTED_DEMON_QUEST_1, 460},
		{NpcID.TORMENTED_DEMON_QUEST_2, 460},
		{NpcID.INVISIBLE_TORMENTED_DEMON_1, 460},
		{NpcID.INVISIBLE_TORMENTED_DEMON_2, 460},
		{NpcID.INVISIBLE_TORMENTED_DEMON_QUEST_1, 460},
		{NpcID.INVISIBLE_TORMENTED_DEMON_QUEST_2, 460},
		{NpcID.SARIM_SEAGULL_PIER_BIG_OUTDOORS, 50},
		{NpcID.CRYSTAL_EXTRACTOR_HEADBAR, 140},
		{NpcID.SAILING_BT_GWENITH_GLIDE_CRYSTAL_STEERING_HEADBAR_NPC, 140},
		{NpcID.GIANT_WASP, 200},
		{NpcID.GIANT_WASP_RED, 200},
		{NpcID.BLOODWOOD_HEADBAR_NPC, 300},
		{NpcID.PLAYER_AXE_CLICKBOX, 200},
		{NpcID.PLAYER_AXE_CLICKBOX_VIS, 200},
		{NpcID.TFA_EZEKIEL_SWORD, 300},
		{NpcID.TFA_EZEKIEL_SWORD_NOOP, 300},
		{NpcID.TFA_GUARDIAN, 660},
		{NpcID.TFA_GUARDIAN_UNSHIELDED, 660},
		{NpcID.TFA_OCELLUS_P2_SWORD_HITBOX, 900},
		{NpcID.TFA_OCELLUS_P3_VOID_VINE, 50},
		{NpcID.TFA_XARTH, 460}
	};
	private static final Map<Integer, Integer> SPLAT_HEIGHT = new HashMap<>();

	static
	{
		for (int[] e : NPC_SPLAT_HEIGHTS)
		{
			SPLAT_HEIGHT.put(e[0], e[1]);
		}
	}

	private final Client client;
	private final FancyHitsplatsPlugin plugin;
	private final FancyHitsplatsConfig config;
	// Chunky font for MapleStory; for WoW, Friz Quadrata if installed, else a clean sans-serif
	// with even-height digits. The first installed font in each list wins.
	private final Font mapleFont = pickFont(Font.SANS_SERIF, "Arial Black", "Impact", "Verdana");
	private final Font wowFont = pickFont(Font.SANS_SERIF, "Friz Quadrata TT", "Trebuchet MS", "Tahoma", "Arial");

	@Inject
	FancyHitsplatsOverlay(Client client, FancyHitsplatsPlugin plugin, FancyHitsplatsConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.UNDER_WIDGETS); // above the game's hitsplats (ABOVE_SCENE drew under them), below interfaces
		setPriority(PRIORITY_HIGH);
	}

	private static Font pickFont(String fallback, String... names)
	{
		Set<String> have = new HashSet<>(Arrays.asList(
			GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
		for (String n : names)
		{
			if (have.contains(n))
			{
				return new Font(n, Font.BOLD, 24);
			}
		}
		return new Font(fallback, Font.BOLD, 24);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		long now = System.currentTimeMillis();
		long duration = config.duration();
		plugin.prune(now, duration);
		if (plugin.hits.isEmpty() && plugin.bursts.isEmpty())
		{
			return null;
		}
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		Composite oldComposite = g.getComposite();
		boolean maple = config.style() == FancyHitsplatsConfig.Style.MAPLESTORY;
		float scale = config.scale() / 100f;

		// Pass 1: backing plates over the game's splats, so no plate covers another hit's number.
		if (config.plate() && config.start() == FancyHitsplatsConfig.Start.OVER_HITSPLAT)
		{
			for (FloatingHit h : plugin.hits)
			{
				if (h.kind != FloatingHit.Kind.NORMAL)
				{
					drawPlate(g, h, now);
				}
			}
		}

		// Pass 2: image bursts, under the numbers.
		for (Burst b : plugin.bursts)
		{
			drawBurstPicture(g, b, now, scale);
		}

		for (FloatingHit h : plugin.hits)
		{
			Actor a = h.actor;
			if (h.kind == FloatingHit.Kind.NORMAL)
			{
				drawNormal(g, h);
				continue;
			}
			Point head;
			if (config.start() == FancyHitsplatsConfig.Start.OVER_HITSPLAT)
			{
				// Same spot the game draws its splat: half the model's height, plus the slot offset.
				// drawMaple() centres the number about 9px above the point it's given, so shift down to match.
				Point c = splatCentre(a);
				if (c == null)
				{
					continue;
				}
				// Slots spread 1.4x wider than the game's: the fancy numbers are bigger than its splats,
				// so at 1x two splats up at once piled on top of each other.
				int[] off = SLOT_OFFSETS[h.slot];
				int dy = maple ? Math.round(9 * scale) : 0; // WoW numbers are drawn centred on the point
				head = new Point(c.getX() + Math.round(off[0] * 1.4f * scale), c.getY() + Math.round(off[1] * 1.4f * scale) + dy);
			}
			else
			{
				head = a.getCanvasTextLocation(g, "0", a.getLogicalHeight() + HEAD_OFFSET);
				if (head == null)
				{
					continue;
				}
			}
			long el = now - h.startMs;
			// Holds still (t = 0) while the game's splat is up, then floats and fades.
			float t = Math.max(0f, Math.min(1f, (now - h.releaseMs) / (float) duration));
			if (maple)
			{
				drawMaple(g, h, head, el, t, scale);
			}
			else
			{
				drawWow(g, h, head, el, t, scale);
			}
		}
		g.setComposite(oldComposite);
		return null;
	}

	/**
	 * The game's own splat, enlarged, exactly over the real one (its slot), so it hides the real
	 * splat; only the number on top is styled. Opaque while the real splat is up, then fades out
	 * over 150ms.
	 */
	private void drawPlate(Graphics2D g, FloatingHit h, long now)
	{
		float a = now < h.releaseMs ? 1f : 1f - (now - h.releaseMs) / 150f;
		BufferedImage img = plugin.originals.get(h.sprite);
		if (a <= 0f || img == null)
		{
			return;
		}
		Point c = splatCentre(h.actor);
		if (c == null)
		{
			return;
		}
		float k = config.plateSize() / 100f;
		int w = Math.round(img.getWidth() * k);
		int ht = Math.round(img.getHeight() * k);
		int[] off = SLOT_OFFSETS[h.slot];
		int x = c.getX() + off[0] - w / 2;
		int y = c.getY() + off[1] - ht / 2;
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
		g.drawImage(img, x, y, w, ht, null);
		g.setComposite(AlphaComposite.SrcOver);
	}

	/**
	 * A picture flying out of the splat like a sticker: pops from small to full size with an
	 * overshoot, flies outward along its angle (easing out, drifting up a touch), spins a little,
	 * white sticker border + soft shadow, fades over the second half.
	 */
	private void drawBurstPicture(Graphics2D g, Burst b, long now, float scale)
	{
		long el = now - b.startMs;
		if (el < 0)
		{
			return;
		}
		Point c = splatCentre(b.actor);
		if (c == null)
		{
			return;
		}
		drawBurstAt(g, b, c.getX() + SLOT_OFFSETS[b.slot][0], c.getY() + SLOT_OFFSETS[b.slot][1], el, scale);
	}

	private void drawBurstAt(Graphics2D g, Burst b, float cx, float cy, long el, float scale)
	{
		float t = Math.min(1f, el / (float) FancyHitsplatsPlugin.BURST_MS);
		float ease = 1f - (1f - t) * (1f - t) * (1f - t);
		float cardSize = config.burstSize() * scale;
		// Start above the hit (half a picture + 30px up) so the pictures never sit on the target,
		// and fly out far enough - scaled to the picture size - that the three don't overlap.
		float originY = cy - cardSize * 0.5f - 30f * scale;
		float dist = (cardSize * 1.1f + 24f * scale) * b.reach * ease;
		float x = cx + (float) Math.cos(b.angle) * dist;
		float y = originY + (float) Math.sin(b.angle) * dist - 12f * scale * t;
		// pop: 0.2 -> 1.15 -> 1.0
		float pop = t < 0.15f ? 0.2f + 0.95f * (t / 0.15f) : (t < 0.25f ? 1.15f - 0.15f * ((t - 0.15f) / 0.1f) : 1f);
		float alpha = t < 0.55f ? 1f : Math.max(0f, 1f - (t - 0.55f) / 0.45f);
		BufferedImage img = b.image;
		double fit = config.burstSize() * scale / (double) Math.max(img.getWidth(), img.getHeight());
		double w = img.getWidth() * fit * pop;
		double h = img.getHeight() * fit * pop;

		AffineTransform old = g.getTransform();
		Composite oldC = g.getComposite();
		Shape oldClip = g.getClip();
		g.translate(x, y);
		g.rotate(Math.toRadians(b.spin * ease));
		RoundRectangle2D card = new RoundRectangle2D.Double(-w / 2 - 3, -h / 2 - 3, w + 6, h + 6, 10, 10);
		g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha * 0.35f));
		g.setColor(Color.BLACK);
		g.translate(2, 3);
		g.fill(card);
		g.translate(-2, -3);
		g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
		g.setColor(Color.WHITE);
		g.fill(card);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.clip(new RoundRectangle2D.Double(-w / 2, -h / 2, w, h, 7, 7));
		g.drawImage(img, (int) Math.round(-w / 2), (int) Math.round(-h / 2), (int) Math.round(w), (int) Math.round(h), null);
		// Transform back FIRST: oldClip is in the untransformed space; restoring it while still
		// rotated clipped everything drawn afterwards (the numbers vanished, pictures got cut).
		g.setTransform(old);
		g.setClip(oldClip);
		g.setComposite(oldC);
	}

	/** Where the game centres an actor's splats: half its (possibly overridden) height + animation offset. */
	private Point splatCentre(Actor a)
	{
		LocalPoint lp = a.getLocalLocation();
		if (lp == null)
		{
			return null;
		}
		int height = a.getLogicalHeight();
		if (a instanceof NPC)
		{
			height = SPLAT_HEIGHT.getOrDefault(((NPC) a).getId(), height);
		}
		height += a.getAnimationHeightOffset();
		return Perspective.localToCanvas(client, lp, client.getPlane(), height / 2);
	}

	// A splat drawn like the game's own: its original image at the game's slot, small white number.
	private void drawNormal(Graphics2D g, FloatingHit h)
	{
		Point c = splatCentre(h.actor);
		if (c == null)
		{
			return;
		}
		g.setComposite(AlphaComposite.SrcOver);
		int[] off = SLOT_OFFSETS[h.slot];
		int x = c.getX() + off[0];
		int y = c.getY() + off[1];
		BufferedImage img = plugin.originals.get(h.sprite);
		if (img != null)
		{
			g.drawImage(img, x - img.getWidth() / 2, y - img.getHeight() / 2, null);
		}
		String text = String.valueOf(h.amount);
		g.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics fm = g.getFontMetrics();
		int tx = x - fm.stringWidth(text) / 2;
		int ty = y + fm.getAscent() / 2 - 1;
		g.setColor(Color.BLACK);
		g.drawString(text, tx + 1, ty + 1);
		g.setColor(Color.WHITE);
		g.drawString(text, tx, ty);
	}

	// ---- MapleStory: chunky gradient digits with a thick dark outline, each digit popping in
	// a beat after the last, packed tight so they overlap; new hits stack one line higher.
	private void drawMaple(Graphics2D g, FloatingHit h, Point head, long el, float t, float scale)
	{
		float size = 26 * scale * (h.crit ? 1.3f : 1f) * (h.kind == FloatingHit.Kind.DOT ? 0.75f : 1f);
		Color top;
		Color bottom;
		Color edge;
		switch (h.kind)
		{
			case TAKEN:
				top = new Color(0xF3DCFF);
				bottom = new Color(0x9B30FF);
				edge = new Color(0x26004A);
				break;
			case OTHER:
				top = new Color(0xF0F0F0);
				bottom = new Color(0x8C8C8C);
				edge = new Color(0x1E1E1E);
				break;
			case HEAL:
				top = new Color(0xDCFFDC);
				bottom = new Color(0x22C03A);
				edge = new Color(0x053A0E);
				break;
			case DOT:
				top = new Color(0xE4FFB8);
				bottom = new Color(0x5FA800);
				edge = new Color(0x1C3300);
				break;
			case MISS:
				top = new Color(0xEAF5FF);
				bottom = new Color(0x5E93D6);
				edge = new Color(0x0E2445);
				break;
			default:
				if (h.crit)
				{
					top = new Color(0xFFE3EE);
					bottom = new Color(0xFF1E4E);
					edge = new Color(0x45000F);
				}
				else
				{
					top = new Color(0xFFF6B5);
					bottom = new Color(0xFF8000);
					edge = new Color(0x3A1400);
				}
		}

		float alpha = t < 0.7f ? 1f : Math.max(0f, (1f - t) / 0.3f);
		g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

		String text = h.text(FancyHitsplatsConfig.Style.MAPLESTORY);
		Font font = mapleFont.deriveFont(size);
		GlyphVector gv = font.createGlyphVector(g.getFontRenderContext(), text);
		Rectangle2D vb = gv.getVisualBounds();
		float pack = h.kind == FloatingHit.Kind.MISS ? 0.95f : 0.86f; // digits overlap like MapleStory's
		float width = (float) vb.getWidth() * pack;
		float lineH = 26 * scale * 1.05f;
		float cx = head.getX();
		float baseY = head.getY() - h.stack * lineH - 18 * scale * t;
		if (h.crit && h.stack > 0)
		{
			baseY -= size * 0.25f; // crits are bigger - keep them off the number below
		}
		float left = cx - width / 2f;

		if (h.crit)
		{
			drawBurst(g, cx, baseY - size * 0.35f, size * 1.25f, el, alpha);
			drawLabel(g, "CRITICAL", cx, baseY - size * 1.05f, size * 0.42f, new Color(0xFFE15A), new Color(0x5A1A00));
		}

		// See-through numbers: outline the digits as ONE shape - they overlap, so per-digit outlines
		// cut into their neighbours.
		boolean seeThrough = config.numberOpacity() < 100;
		Area whole = seeThrough ? new Area() : null;
		for (int i = 0; i < gv.getNumGlyphs(); i++)
		{
			long local = el - i * 35L;
			if (local < 0)
			{
				continue;
			}
			// Pop: starts 1.6x and settles to 1.0 over 110ms.
			float p = Math.min(1f, local / 110f);
			float pop = 1f + 0.6f * (1f - p) * (1f - p);
			Shape glyph = gv.getGlyphOutline(i);
			Rectangle2D gb = glyph.getBounds2D();
			Point2D gp = gv.getGlyphPosition(i);
			float gx = left + (float) (gp.getX() - vb.getX()) * pack;
			float gcx = gx + (float) gb.getWidth() / 2f;
			float gcy = baseY - (float) gb.getHeight() / 2f;
			// small bounce per digit, settles
			float bounce = (1f - p) * -6f * scale;
			AffineTransform at = new AffineTransform();
			at.translate(gcx, gcy + bounce);
			at.scale(pop, pop);
			at.translate(-(gb.getX() + gb.getWidth() / 2), -(gb.getY() + gb.getHeight() / 2));
			Shape s = at.createTransformedShape(glyph);
			if (seeThrough)
			{
				whole.add(new Area(s));
			}
			else
			{
				strokeAndFill(g, s, Math.max(2f, size * 0.16f), edge, top, bottom);
			}
		}
		if (seeThrough && !whole.isEmpty())
		{
			strokeAndFill(g, whole, Math.max(2f, size * 0.16f), edge, top, bottom);
		}
	}

	private static void drawBurst(Graphics2D g, float cx, float cy, float r, long el, float alpha)
	{
		float grow = Math.min(1f, el / 140f);
		int points = 10;
		Polygon star = new Polygon();
		double spin = el / 900.0;
		for (int i = 0; i < points * 2; i++)
		{
			double ang = spin + Math.PI * i / points;
			double rad = (i % 2 == 0 ? r : r * 0.45) * grow;
			star.addPoint((int) (cx + Math.cos(ang) * rad), (int) (cy + Math.sin(ang) * rad * 0.8));
		}
		Composite c = g.getComposite();
		g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha * 0.55f));
		g.setPaint(new GradientPaint(cx, cy - r, new Color(0xFFF3A0), cx, cy + r, new Color(0xFF7A1A)));
		g.fill(star);
		g.setComposite(c);
	}

	private void drawLabel(Graphics2D g, String text, float cx, float y, float size, Color fill, Color edge)
	{
		GlyphVector gv = mapleFont.deriveFont(size).createGlyphVector(g.getFontRenderContext(), text);
		Rectangle2D b = gv.getVisualBounds();
		Shape s = gv.getOutline((float) (cx - b.getWidth() / 2 - b.getX()), y);
		strokeAndFill(g, s, Math.max(1.5f, size * 0.22f), edge, fill.brighter(), fill);
	}

	// ---- World of Warcraft: clean numbers, thin black outline, drifting up and to one
	// side; crits pop in big and settle a bit larger than normal hits.
	private void drawWow(Graphics2D g, FloatingHit h, Point head, long el, float t, float scale)
	{
		Color fill;
		float size = 22 * scale;
		switch (h.kind)
		{
			case TAKEN:
				fill = new Color(0xFF2B2B);
				break;
			case OTHER:
				fill = new Color(0xBDBDBD);
				size *= 0.85f;
				break;
			case HEAL:
				fill = new Color(0x3CFF3C);
				break;
			case DOT:
				fill = new Color(0xFFD84A);
				size *= 0.8f;
				break;
			case MISS:
				fill = Color.WHITE;
				size *= 0.85f;
				break;
			default:
				fill = h.crit ? new Color(0xFFE600) : Color.WHITE;
		}

		float pop;
		if (h.crit)
		{
			// 2.3x -> 1.5x over 150ms, then holds
			float p = Math.min(1f, el / 150f);
			pop = 2.3f - 0.8f * p;
		}
		else
		{
			float p = Math.min(1f, el / 90f);
			pop = 1.25f - 0.25f * p;
		}
		float alpha = t < 0.6f ? 1f : Math.max(0f, (1f - t) / 0.4f);
		g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

		// Crits hang in place a moment before floating; normal hits rise and drift right away.
		float move = h.crit ? Math.max(0f, (t - 0.25f) / 0.75f) : t;
		float x = head.getX() + h.drift * 34 * scale * move;
		float y = head.getY() - 70 * scale * move;

		String text = h.text(FancyHitsplatsConfig.Style.WORLD_OF_WARCRAFT);
		GlyphVector gv = wowFont.deriveFont(size).createGlyphVector(g.getFontRenderContext(), text);
		Rectangle2D b = gv.getVisualBounds();
		AffineTransform at = new AffineTransform();
		at.translate(x, y);
		at.scale(pop, pop);
		at.translate(-(b.getX() + b.getWidth() / 2), -(b.getY() + b.getHeight() / 2));
		Shape s = at.createTransformedShape(gv.getOutline());
		strokeAndFill(g, s, 2.6f * Math.max(1f, pop * 0.8f), Color.BLACK, fill, blend(fill, new Color(0x9A9A9A)));
	}

	private static Color blend(Color a, Color b)
	{
		return new Color((a.getRed() * 3 + b.getRed()) / 4, (a.getGreen() * 3 + b.getGreen()) / 4, (a.getBlue() * 3 + b.getBlue()) / 4);
	}

	private void strokeAndFill(Graphics2D g, Shape s, float stroke, Color edge, Color top, Color bottom)
	{
		Rectangle2D b = s.getBounds2D();
		GradientPaint fill = new GradientPaint(0, (float) b.getMinY(), top, 0, (float) b.getMaxY(), bottom);
		float opacity = config.numberOpacity() / 100f;
		if (opacity >= 1f)
		{
			g.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.setColor(edge);
			g.draw(s);
			g.setPaint(fill);
			g.fill(s);
			return;
		}
		// See-through numbers: a bold outline drawn only OUTSIDE the digits (so it doesn't eat into
		// them), then the digits filled at the chosen opacity so what's behind shows through.
		Area outline = new Area(
			new BasicStroke(stroke * 2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND).createStrokedShape(s));
		outline.subtract(new Area(s));
		g.setColor(edge);
		g.fill(outline);
		if (opacity > 0f)
		{
			Composite c = g.getComposite();
			float base = c instanceof AlphaComposite ? ((AlphaComposite) c).getAlpha() : 1f;
			g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, base * opacity));
			g.setPaint(fill);
			g.fill(s);
			g.setComposite(c);
		}
	}
}
