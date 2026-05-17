package com.igormaznitsa.langtrainer.modules.bricks;

import com.igormaznitsa.langtrainer.ui.LangTrainerFonts;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

final class BricksFieldCanvas extends JPanel {

  private static final float BRICK_FONT_PT = 19f;
  private static final int ZONE_LINE = 1;
  private static final int INNER_PAD = 8;
  private static final int ROW_V_GAP = 8;
  private static final int INTER_ZONE_GAP = 6;
  private static final float SCALE_MIN = 0.16f;

  private final Color poolZoneBg;
  private final Color poolZoneLine;
  private final Color buildZoneBg;
  private final Color buildZoneLine;
  private final Color poolBrickFill;
  private final List<PlacedBrick> poolPlaced = new ArrayList<>();
  private final List<PlacedBrick> buildPlaced = new ArrayList<>();
  private final List<BufferedImage> poolRaster = new ArrayList<>();
  private final List<BufferedImage> buildRaster = new ArrayList<>();
  private BufferedImage buildSuffixRaster;
  private Color buildBrickFill;
  private List<String> wordTokens = List.of();
  private String fixedEndSuffix;
  private List<Integer> poolIds;
  private List<Integer> buildIds;
  private int buildSuffixX;
  private int buildSuffixY;
  private int buildSuffixW;
  private int buildSuffixH;
  private int[] baseBrickW;
  private int baseBrickH;
  private Font baseBrickFont;
  private float brickScale = 1f;
  private int poolZoneTop;
  private int poolZoneH;
  private int buildZoneTop;
  private int buildZoneH;
  private int contentInnerW;
  private int draggingId = -1;
  private BufferedImage dragGhostRaster;
  private int dragGhostW;
  private int dragGhostH;
  private int dragOffsetX;
  private int dragOffsetY;
  private int dragPointerX;
  private int dragPointerY;

  private FieldHost host;

  BricksFieldCanvas(
      final Color poolZoneBg,
      final Color poolZoneLine,
      final Color buildZoneBg,
      final Color buildZoneLine,
      final Color poolBrickFill,
      final Color buildBrickFill) {
    this.poolZoneBg = Objects.requireNonNull(poolZoneBg);
    this.poolZoneLine = Objects.requireNonNull(poolZoneLine);
    this.buildZoneBg = Objects.requireNonNull(buildZoneBg);
    this.buildZoneLine = Objects.requireNonNull(buildZoneLine);
    this.poolBrickFill = Objects.requireNonNull(poolBrickFill);
    this.buildBrickFill = Objects.requireNonNull(buildBrickFill);
    this.setOpaque(true);
    this.setBackground(new Color(248, 250, 252));
    this.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mousePressed(final MouseEvent e) {
            BricksFieldCanvas.this.onMousePressedLocal(e);
          }

          @Override
          public void mouseReleased(final MouseEvent e) {
            BricksFieldCanvas.this.onMouseReleasedLocal(e);
          }
        });
  }

  void setHost(final FieldHost host) {
    this.host = Objects.requireNonNull(host, "host");
  }

  void bindLists(
      final List<Integer> poolIds,
      final List<Integer> buildIds,
      final List<String> wordTokens,
      final String fixedEndSuffix) {
    this.poolIds = Objects.requireNonNull(poolIds);
    this.buildIds = Objects.requireNonNull(buildIds);
    this.wordTokens = List.copyOf(Objects.requireNonNull(wordTokens));
    this.fixedEndSuffix =
        fixedEndSuffix == null || fixedEndSuffix.isEmpty() ? null : fixedEndSuffix;
    this.ensureBaseMeasures();
    this.recomputeLayoutAndRepaint();
  }

  void setBuildBrickFill(final Color fill) {
    this.buildBrickFill = Objects.requireNonNull(fill);
    this.invalidateBuildRasters();
    this.recomputeLayoutAndRepaint();
  }

  void setDraggingFromPromote(
      final int id,
      final float grabFracX,
      final float grabFracY,
      final Point pointerScreen) {
    this.draggingId = id;
    this.recomputeLayoutIfNeeded();
    this.dragGhostRaster =
        BrickImageRenderer.renderBrickImage(
            this.wordTokens.get(id), this.poolBrickFill, this.layoutFont());
    this.dragGhostW = Math.max(1, Math.round(this.dragGhostRaster.getWidth() * this.brickScale));
    this.dragGhostH = Math.max(1, Math.round(this.dragGhostRaster.getHeight() * this.brickScale));
    this.dragOffsetX =
        Math.min(
            this.dragGhostW - 1,
            Math.max(0, Math.round(grabFracX * this.dragGhostW)));
    this.dragOffsetY =
        Math.min(
            this.dragGhostH - 1,
            Math.max(0, Math.round(grabFracY * this.dragGhostH)));
    this.updateDragPointerFromScreen(pointerScreen);
    this.repaint();
  }

  void clearDragging() {
    this.draggingId = -1;
    this.dragGhostRaster = null;
    this.dragGhostW = 0;
    this.dragGhostH = 0;
    this.recomputeLayoutAndRepaint();
  }

  void updateDragPointerFromScreen(final Point screen) {
    if (this.draggingId < 0 || screen == null) {
      return;
    }
    final Point p = new Point(screen);
    SwingUtilities.convertPointFromScreen(p, this);
    this.dragPointerX = p.x;
    this.dragPointerY = p.y;
    this.repaint();
  }

  Font layoutFont() {
    return LangTrainerFonts.MONO_NL_REGULAR.atPoints(BRICK_FONT_PT * this.brickScale);
  }

  Rectangle buildBricksUnionInCanvas() {
    int minX = Integer.MAX_VALUE;
    int minY = Integer.MAX_VALUE;
    int maxX = Integer.MIN_VALUE;
    int maxY = Integer.MIN_VALUE;
    for (final PlacedBrick p : this.buildPlaced) {
      minX = Math.min(minX, p.x);
      minY = Math.min(minY, p.y);
      maxX = Math.max(maxX, p.x + p.w);
      maxY = Math.max(maxY, p.y + p.h);
    }
    if (minX > maxX) {
      if (this.fixedEndSuffix != null && this.buildSuffixW > 0) {
        return new Rectangle(
            this.buildSuffixX, this.buildSuffixY, this.buildSuffixW, this.buildSuffixH);
      }
      return new Rectangle(
          INNER_PAD + ZONE_LINE,
          this.buildZoneTop + INNER_PAD + ZONE_LINE,
          Math.max(1, this.getWidth() - 2 * (INNER_PAD + ZONE_LINE)),
          Math.max(1, this.buildZoneH - 2 * (INNER_PAD + ZONE_LINE)));
    }
    if (this.fixedEndSuffix != null && this.buildSuffixW > 0) {
      minX = Math.min(minX, this.buildSuffixX);
      minY = Math.min(minY, this.buildSuffixY);
      maxX = Math.max(maxX, this.buildSuffixX + this.buildSuffixW);
      maxY = Math.max(maxY, this.buildSuffixY + this.buildSuffixH);
    }
    return new Rectangle(minX, minY, maxX - minX, maxY - minY);
  }

  DropResolution resolveDropFromScreen(final Point screen) {
    final Point p = new Point(screen);
    SwingUtilities.convertPointFromScreen(p, this);
    return this.resolveDropAtCanvasPoint(p);
  }

  BrickPick pickBrickAtCanvasPoint(final Point canvasPoint) {
    this.ensureLayoutLists();
    for (final PlacedBrick b : this.poolPlaced) {
      if (b.contains(canvasPoint)) {
        return new BrickPick(
            b.id,
            canvasPoint.x - b.x,
            canvasPoint.y - b.y,
            b.w,
            b.h);
      }
    }
    for (final PlacedBrick b : this.buildPlaced) {
      if (b.contains(canvasPoint)) {
        return new BrickPick(
            b.id,
            canvasPoint.x - b.x,
            canvasPoint.y - b.y,
            b.w,
            b.h);
      }
    }
    return null;
  }

  private void onMousePressedLocal(final MouseEvent e) {
    if (this.draggingId >= 0 || this.host == null) {
      return;
    }
    final BrickPick pick = this.pickBrickAtCanvasPoint(e.getPoint());
    if (pick == null) {
      return;
    }
    this.host.onFieldBrickPressed(pick, e.getLocationOnScreen());
  }

  private void onMouseReleasedLocal(final MouseEvent e) {
    if (this.host != null && BricksWorkPanel.isPrimaryReleaseForDrop(e)) {
      this.host.onFieldReleasedPrimary(e);
    }
  }

  private void ensureBaseMeasures() {
    this.baseBrickFont = LangTrainerFonts.MONO_NL_REGULAR.atPoints(BRICK_FONT_PT);
    this.baseBrickH = BrickImageRenderer.measureBrickHeightPx(this.baseBrickFont);
    final int n = this.wordTokens.size();
    this.baseBrickW = new int[n];
    for (int i = 0; i < n; i++) {
      this.baseBrickW[i] =
          BrickImageRenderer.measureBrickWidthPx(this.wordTokens.get(i), this.baseBrickFont);
    }
  }

  private void invalidateBuildRasters() {
    this.buildRaster.clear();
  }

  private void ensureLayoutLists() {
    if (this.getWidth() <= 0) {
      return;
    }
    this.recomputeLayoutIfNeeded();
  }

  private void recomputeLayoutAndRepaint() {
    this.recomputeLayoutIfNeeded();
    this.revalidate();
    this.repaint();
  }

  private void recomputeLayoutIfNeeded() {
    int w = this.getWidth();
    if (w <= 0 && this.getParent() != null) {
      w = Math.max(0, this.getParent().getWidth());
    }
    if (w <= 0 || this.poolIds == null || this.baseBrickW == null) {
      return;
    }
    this.contentInnerW = w - 2 * (INNER_PAD + ZONE_LINE);
    this.brickScale = this.resolveScaleForTwoRows(this.contentInnerW);
    final int brickDrawH = Math.max(1, Math.round(this.baseBrickH * this.brickScale));
    final int rowContentH = 2 * brickDrawH + ROW_V_GAP;
    this.poolZoneH = INNER_PAD * 2 + ZONE_LINE * 2 + rowContentH;
    this.buildZoneH = this.poolZoneH;
    this.poolZoneTop = 0;
    this.buildZoneTop = this.poolZoneH + INTER_ZONE_GAP;
    this.refreshRasters();
    this.poolPlaced.clear();
    this.buildPlaced.clear();
    this.flowPlace(
        this.poolIds,
        this.poolPlaced,
        INNER_PAD + ZONE_LINE,
        this.poolZoneTop + INNER_PAD + ZONE_LINE,
        this.draggingId);
    final int buildOriginX = INNER_PAD + ZONE_LINE;
    final int buildOriginY = this.buildZoneTop + INNER_PAD + ZONE_LINE;
    final FlowEnd buildEnd =
        this.flowPlace(
            this.buildIds,
            this.buildPlaced,
            buildOriginX,
            buildOriginY,
            this.draggingId);
    this.layoutBuildFixedSuffix(buildOriginX, buildOriginY, buildEnd);
    final int totalH = this.buildZoneTop + this.buildZoneH;
    this.setPreferredSize(new Dimension(w, totalH));
  }

  private float resolveScaleForTwoRows(final int innerW) {
    float s = 1f;
    while (s >= SCALE_MIN
        && !(this.fitsTwoRows(innerW, s, this.poolIds, this.draggingId, 0)
        && this.fitsTwoRows(
        innerW, s, this.buildIds, this.draggingId, this.suffixTailWidthPx(s)))) {
      s -= 0.018f;
    }
    return Math.max(SCALE_MIN, s);
  }

  private boolean fitsTwoRows(
      final int innerW,
      final float scale,
      final List<Integer> ids,
      final int excludeId,
      final int extraTailWidth) {
    int row = 0;
    int x = 0;
    final int hgap = Math.max(1, Math.round(BrickImageRenderer.BRICK_FLOW_H_GAP * scale));
    for (final Integer idObj : ids) {
      final int id = idObj;
      if (id == excludeId) {
        continue;
      }
      final int bw = Math.max(1, Math.round(this.baseBrickW[id] * scale));
      if (x > 0 && x + bw > innerW) {
        row++;
        x = 0;
      }
      if (row > 1) {
        return false;
      }
      x += bw + hgap;
    }
    if (row > 1) {
      return false;
    }
    return x + extraTailWidth <= innerW;
  }

  private int suffixTailWidthPx(final float scale) {
    if (this.fixedEndSuffix == null) {
      return 0;
    }
    final int hgap = Math.max(1, Math.round(BrickImageRenderer.BRICK_FLOW_H_GAP * scale));
    final int textW =
        Math.max(
            1,
            Math.round(
                BrickImageRenderer.measureBrickWidthPx(this.fixedEndSuffix, this.layoutFont())
                    * scale));
    final boolean hasBricks =
        this.buildIds.stream().anyMatch(id -> id != this.draggingId);
    return textW + (hasBricks ? hgap : 0);
  }

  private FlowEnd flowPlace(
      final List<Integer> ids,
      final List<PlacedBrick> out,
      final int originX,
      final int originY,
      final int excludeId) {
    int row = 0;
    int x = 0;
    int y = 0;
    final int hgap = Math.max(1, Math.round(BrickImageRenderer.BRICK_FLOW_H_GAP * this.brickScale));
    final int brickDrawH = Math.max(1, Math.round(this.baseBrickH * this.brickScale));
    for (final Integer idObj : ids) {
      final int id = idObj;
      if (id == excludeId) {
        continue;
      }
      final int bw = Math.max(1, Math.round(this.baseBrickW[id] * this.brickScale));
      if (x > 0 && x + bw > this.contentInnerW) {
        row++;
        x = 0;
        y += brickDrawH + ROW_V_GAP;
      }
      out.add(new PlacedBrick(id, originX + x, originY + y, bw, brickDrawH));
      x += bw + hgap;
    }
    return new FlowEnd(originX + x, originY + y, row, brickDrawH);
  }

  private void layoutBuildFixedSuffix(
      final int buildOriginX, final int buildOriginY, final FlowEnd buildEnd) {
    if (this.fixedEndSuffix == null) {
      this.buildSuffixW = 0;
      this.buildSuffixH = 0;
      return;
    }
    final Font font = this.layoutFont();
    this.buildSuffixW =
        Math.max(
            1,
            Math.round(
                BrickImageRenderer.measureBrickWidthPx(this.fixedEndSuffix, font)
                    * this.brickScale));
    this.buildSuffixH = Math.max(1, Math.round(this.baseBrickH * this.brickScale));
    int x;
    int y;
    if (this.buildPlaced.isEmpty()) {
      x = buildOriginX;
      y = buildOriginY;
    } else {
      x = buildEnd.nextX();
      y = buildEnd.y();
      if (x + this.buildSuffixW > buildOriginX + this.contentInnerW) {
        y += buildEnd.brickDrawH() + ROW_V_GAP;
        x = buildOriginX;
      }
    }
    this.buildSuffixX = x;
    this.buildSuffixY = y + Math.max(0, (buildEnd.brickDrawH() - this.buildSuffixH) / 2);
  }

  private void paintBuildFixedSuffix(final Graphics2D g2) {
    if (this.buildSuffixRaster == null || this.buildSuffixW <= 0) {
      return;
    }
    g2.drawImage(
        this.buildSuffixRaster,
        this.buildSuffixX,
        this.buildSuffixY,
        this.buildSuffixW,
        this.buildSuffixH,
        null);
  }

  private void refreshRasters() {
    final Font f = this.layoutFont();
    this.poolRaster.clear();
    for (final int id : this.poolIds) {
      if (id == this.draggingId) {
        continue;
      }
      this.poolRaster.add(
          BrickImageRenderer.renderBrickImage(this.wordTokens.get(id), this.poolBrickFill, f));
    }
    this.buildRaster.clear();
    for (final int id : this.buildIds) {
      if (id == this.draggingId) {
        continue;
      }
      this.buildRaster.add(
          BrickImageRenderer.renderBrickImage(this.wordTokens.get(id), this.buildBrickFill, f));
    }
    this.buildSuffixRaster =
        this.fixedEndSuffix == null
            ? null
            : BrickImageRenderer.renderBrickImage(
            this.fixedEndSuffix, BrickImageRenderer.SUFFIX_BRICK_FILL, f);
  }

  @Override
  public void doLayout() {
    super.doLayout();
    this.recomputeLayoutIfNeeded();
  }

  @Override
  protected void paintComponent(final Graphics g) {
    super.paintComponent(g);
    final Graphics2D g2 = (Graphics2D) g.create();
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
          RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

      this.paintZone(
          g2,
          0,
          this.poolZoneTop,
          this.getWidth(),
          this.poolZoneH,
          this.poolZoneBg,
          this.poolZoneLine);
      this.paintZone(
          g2,
          0,
          this.buildZoneTop,
          this.getWidth(),
          this.buildZoneH,
          this.buildZoneBg,
          this.buildZoneLine);
      int i = 0;
      for (final PlacedBrick p : this.poolPlaced) {
        if (i < this.poolRaster.size()) {
          this.paintScaledRaster(g2, this.poolRaster.get(i), p);
        }
        i++;
      }
      i = 0;
      for (final PlacedBrick p : this.buildPlaced) {
        if (i < this.buildRaster.size()) {
          this.paintScaledRaster(g2, this.buildRaster.get(i), p);
        }
        i++;
      }
      this.paintBuildFixedSuffix(g2);
      if (this.draggingId >= 0 && this.dragGhostRaster != null) {
        final int gx = this.dragPointerX - this.dragOffsetX;
        final int gy = this.dragPointerY - this.dragOffsetY;
        g2.drawImage(this.dragGhostRaster, gx, gy, this.dragGhostW, this.dragGhostH, null);
      }
    } finally {
      g2.dispose();
    }
  }

  private void paintScaledRaster(
      final Graphics2D g2, final BufferedImage img, final PlacedBrick p) {
    g2.drawImage(img, p.x, p.y, p.w, p.h, null);
  }

  private void paintZone(
      final Graphics2D g2,
      final int x,
      final int y,
      final int w,
      final int h,
      final Color fill,
      final Color line) {
    g2.setColor(fill);
    g2.fillRoundRect(x, y, w, h, 8, 8);
    g2.setColor(line);
    g2.drawRoundRect(x + 1, y + 1, w - 2, h - 2, 8, 8);
  }

  private DropResolution resolveDropAtCanvasPoint(final Point p) {
    final Rectangle poolInner =
        new Rectangle(
            INNER_PAD + ZONE_LINE,
            this.poolZoneTop + INNER_PAD + ZONE_LINE,
            this.contentInnerW,
            this.poolZoneH - 2 * (INNER_PAD + ZONE_LINE));
    final Rectangle buildInner =
        new Rectangle(
            INNER_PAD + ZONE_LINE,
            this.buildZoneTop + INNER_PAD + ZONE_LINE,
            this.contentInnerW,
            this.buildZoneH - 2 * (INNER_PAD + ZONE_LINE));
    if (poolInner.contains(p)) {
      return DropResolution.pool();
    }
    if (buildInner.contains(p)) {
      final int insert = this.insertIndexInBuild(p);
      return DropResolution.build(insert);
    }
    return DropResolution.pool();
  }

  private int insertIndexInBuild(final Point p) {
    if (this.buildPlaced.isEmpty()) {
      return 0;
    }
    final List<PlacedBrick> ordered = new ArrayList<>(this.buildPlaced);
    ordered.sort(
        (a, b) -> {
          if (a.y != b.y) {
            return Integer.compare(a.y, b.y);
          }
          return Integer.compare(a.x, b.x);
        });
    int insert = 0;
    for (final PlacedBrick b : ordered) {
      final int mid = b.x + b.w / 2;
      final boolean rowHit = p.y >= b.y - 2 && p.y < b.y + b.h + 2;
      if (rowHit && p.x < mid) {
        return insert;
      }
      insert++;
    }
    return insert;
  }

  interface FieldHost {
    void onFieldBrickPressed(BrickPick pick, Point pressScreen);

    void onFieldReleasedPrimary(MouseEvent e);
  }

  record BrickPick(int id, int offsetX, int offsetY, int brickW, int brickH) {
  }

  record DropResolution(boolean intoPool, int buildInsertIndex) {
    static DropResolution pool() {
      return new DropResolution(true, 0);
    }

    static DropResolution build(final int insert) {
      return new DropResolution(false, Math.max(0, insert));
    }
  }

  private record PlacedBrick(int id, int x, int y, int w, int h) {
    boolean contains(final Point p) {
      return p.x >= this.x && p.x < this.x + this.w && p.y >= this.y && p.y < this.y + this.h;
    }
  }

  private record FlowEnd(int nextX, int y, int row, int brickDrawH) {
  }
}
