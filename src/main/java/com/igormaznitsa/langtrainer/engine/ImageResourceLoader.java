package com.igormaznitsa.langtrainer.engine;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;

public final class ImageResourceLoader {

  private ImageResourceLoader() {
  }

  public static Icon loadIcon(final String resourcePath, final int width, final int height) {
    Icon result;
    if (resourcePath.toLowerCase().endsWith(".svg")) {
      result = new ImageIcon(loadSvgImage(resourcePath, width, height));
    } else {
      final URL resource = ImageResourceLoader.class.getResource(resourcePath);
      if (resource == null) {
        throw new IllegalArgumentException("Resource is not found: " + resourcePath);
      }
      final ImageIcon imageIcon = new ImageIcon(resource);
      final Image scaled =
          imageIcon.getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH);
      result = new ImageIcon(scaled);
    }
    return result;
  }

  public static Image loadImage(final String resourcePath, final int width, final int height) {
    final Icon icon = loadIcon(resourcePath, width, height);
    final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    final java.awt.Graphics2D graphics = image.createGraphics();
    try {
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
      final PNGTranscoder transcoder = new PNGTranscoder();
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
