package com.igormaznitsa.langtrainer.engine;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import org.apache.batik.gvt.renderer.ImageRenderer;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;

public final class ImageResourceLoader {

  private static final RenderingHints HIGH_QUALITY_DRAWING_HINTS;

  static {
    final RenderingHints rh = new RenderingHints(null);
    rh.put(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    rh.put(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    rh.put(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    rh.put(
        RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
    rh.put(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
    rh.put(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    rh.put(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    rh.put(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    rh.put(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_DISABLE);
    HIGH_QUALITY_DRAWING_HINTS = rh;
  }

  private ImageResourceLoader() {
  }

  public static void applyHighQualityDrawingHints(final Graphics2D graphics) {
    graphics.addRenderingHints(HIGH_QUALITY_DRAWING_HINTS);
  }

  public static Icon loadIcon(final String resourcePath, final int width, final int height) {
    if (resourcePath.toLowerCase().endsWith(".svg")) {
      return new ImageIcon(loadSvgImage(resourcePath, width, height));
    }
    final URL resource = ImageResourceLoader.class.getResource(resourcePath);
    if (resource == null) {
      throw new IllegalArgumentException("Resource is not found: " + resourcePath);
    }
    final Image scaled =
        new ImageIcon(resource).getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH);
    return new ImageIcon(scaled);
  }

  public static Image loadImage(final String resourcePath, final int width, final int height) {
    final Icon icon = loadIcon(resourcePath, width, height);
    final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    final Graphics2D graphics = image.createGraphics();
    try {
      applyHighQualityDrawingHints(graphics);
      icon.paintIcon(null, graphics, 0, 0);
    } finally {
      graphics.dispose();
    }
    return image;
  }

  private static BufferedImage loadSvgImage(
      final String resourcePath,
      final int width,
      final int height) {
    final BufferedImage result;
    final URL resource = ImageResourceLoader.class.getResource(resourcePath);
    if (resource == null) {
      throw new IllegalArgumentException("Resource is not found: " + resourcePath);
    }
    try {
      final PNGTranscoder transcoder = new PNGTranscoder() {
        @Override
        protected ImageRenderer createRenderer() {
          final ImageRenderer renderer = super.createRenderer();
          final RenderingHints merged = new RenderingHints(null);
          final RenderingHints existing = renderer.getRenderingHints();
          if (existing != null) {
            merged.putAll(existing);
          }
          merged.putAll(HIGH_QUALITY_DRAWING_HINTS);
          renderer.setRenderingHints(merged);
          return renderer;
        }
      };
      transcoder.addTranscodingHint(PNGTranscoder.KEY_WIDTH, (float) width);
      transcoder.addTranscodingHint(PNGTranscoder.KEY_HEIGHT, (float) height);
      final TranscoderInput input = new TranscoderInput(resource.toExternalForm());
      final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      final TranscoderOutput output = new TranscoderOutput(outputStream);
      transcoder.transcode(input, output);
      outputStream.flush();
      result = ImageIO.read(new ByteArrayInputStream(outputStream.toByteArray()));
    } catch (Exception ex) {
      throw new IllegalStateException("Can't load SVG image: " + resourcePath, ex);
    }
    return result;
  }
}
