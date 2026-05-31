package com.igormaznitsa.langtrainer.engine;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.Strings.CI;

import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BaseMultiResolutionImage;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import org.apache.batik.gvt.renderer.ImageRenderer;
import org.apache.batik.transcoder.SVGAbstractTranscoder;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;

public final class ImageResourceLoader {

  private static final ConcurrentHashMap<String, Icon> ICON_CACHE = new ConcurrentHashMap<>();
  private static final int EMBEDDED_ICON_SIZE = 36;
  private static final int EMBEDDED_IMAGE_MAX_BYTES = 64 * 1024;
  private static final int EMBEDDED_SVG_RASTER_WIDTH = 1024;
  private static final int EMBEDDED_SVG_RASTER_MAX_HEIGHT = 1024;
  private static final int SVG_MARKUP_SCAN_BYTES = 4096;
  private static final int BYTES_PER_KIB = 1024;
  /**
   * Blocks script execution during rasterization; Batik default, set explicitly for untrusted SVG.
   */
  private static final String DISALLOWED_SVG_SCRIPT_TYPES = "";

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

  public static List<Optional<BufferedImage>> loadLineImages(final DialogDefinition definition) {
    requireNonNull(definition, "definition must not be null");
    return definition.lines().stream()
        .map(line -> line.hasImage()
            ? Optional.of(decodeEmbeddedImage(line.image(), "line image"))
            : Optional.<BufferedImage>empty())
        .toList();
  }

  public static void requireLoadableLineImages(final DialogDefinition definition) {
    requireNonNull(definition, "definition must not be null");
    for (int index = 0; index < definition.lines().size(); index++) {
      final DialogLine line = definition.lines().get(index);
      if (line.hasImage()) {
        decodeEmbeddedImage(line.image(), "line Id " + (index + 1));
      }
    }
  }

  public static Icon embeddedImageIcon(final String base64Text) {
    return embeddedImageIcon(base64Text, EMBEDDED_ICON_SIZE, EMBEDDED_ICON_SIZE);
  }

  public static Icon embeddedImageIcon(
      final String base64Text, final int maxWidth, final int maxHeight) {
    return new ImageIcon(
        scaleImageToFit(decodeEmbeddedImage(base64Text, "embedded image"), maxWidth, maxHeight));
  }

  public static String encodeEmbeddedImageFile(final Path path) {
    try {
      final byte[] bytes = Files.readAllBytes(path);
      decodeEmbeddedImageBytes(bytes, path.toString());
      return Base64.getEncoder().encodeToString(bytes);
    } catch (final IllegalStateException ex) {
      throw ex;
    } catch (final Exception ex) {
      throw new IllegalStateException("Can't load image file: " + path, ex);
    }
  }

  public static BufferedImage decodeEmbeddedImage(final String base64Text, final String context) {
    try {
      return decodeEmbeddedImageBytes(decodeBase64ImageText(base64Text), context);
    } catch (final IllegalStateException ex) {
      throw ex;
    } catch (final Exception ex) {
      throw new IllegalStateException("Can't decode " + context + ": " + ex.getMessage(), ex);
    }
  }

  private static byte[] decodeBase64ImageText(final String base64Text) {
    if (base64Text == null || base64Text.isBlank()) {
      throw new IllegalStateException("Image field is empty");
    }
    try {
      final byte[] bytes =
          Base64.getDecoder().decode(stripDataUriPrefix(base64Text).replaceAll("\\s+", ""));
      requireEmbeddedImageSize(bytes);
      return bytes;
    } catch (final IllegalArgumentException ex) {
      throw new IllegalStateException("Image field is not valid Base64", ex);
    }
  }

  private static String stripDataUriPrefix(final String text) {
    final String stripped = text.strip();
    if (!CI.startsWith(stripped, "data:image/")) {
      return stripped;
    }
    final int comma = stripped.indexOf(',');
    if (comma < 0) {
      throw new IllegalStateException("Image data URI has no Base64 payload");
    }
    return stripped.substring(comma + 1);
  }

  private static BufferedImage decodeEmbeddedImageBytes(final byte[] bytes, final String context) {
    requireEmbeddedImageSize(bytes);
    if (isPng(bytes) || isJpeg(bytes)) {
      return decodeRasterImage(bytes, context);
    }
    if (isSvg(bytes)) {
      return decodeSvgImage(bytes, context);
    }
    throw new IllegalStateException(
        "Unsupported image format in " + context + ". Allowed formats: PNG, JPG, SVG.");
  }

  private static void requireEmbeddedImageSize(final byte[] bytes) {
    if (bytes.length <= EMBEDDED_IMAGE_MAX_BYTES) {
      return;
    }
    throw new IllegalStateException(
        "Image is too big: %d KB. Maximum embedded image size is %d KB."
            .formatted(toRoundedUpKiB(bytes.length), EMBEDDED_IMAGE_MAX_BYTES / BYTES_PER_KIB));
  }

  private static int toRoundedUpKiB(final int bytes) {
    return (bytes + BYTES_PER_KIB - 1) / BYTES_PER_KIB;
  }

  private static BufferedImage decodeRasterImage(final byte[] bytes, final String context) {
    try {
      final BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
      if (image == null) {
        throw new IllegalStateException("Unsupported or empty raster image");
      }
      return image;
    } catch (final Exception ex) {
      throw new IllegalStateException("Can't decode raster image in " + context, ex);
    }
  }

  private static BufferedImage decodeSvgImage(final byte[] bytes, final String context) {
    rejectUnsafeSvgMarkup(bytes, context);
    try {
      final PNGTranscoder transcoder = secureEmbeddedSvgTranscoder();
      final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      transcoder.transcode(
          new TranscoderInput(new ByteArrayInputStream(bytes)),
          new TranscoderOutput(outputStream));
      outputStream.flush();
      final BufferedImage image =
          ImageIO.read(new ByteArrayInputStream(outputStream.toByteArray()));
      if (image == null) {
        throw new IllegalStateException("Unsupported or empty SVG image");
      }
      return image;
    } catch (final Exception ex) {
      throw new IllegalStateException("Can't decode SVG image in " + context, ex);
    }
  }

  private static boolean isPng(final byte[] bytes) {
    return bytes.length >= 8
        && bytes[0] == (byte) 0x89
        && bytes[1] == 0x50
        && bytes[2] == 0x4E
        && bytes[3] == 0x47
        && bytes[4] == 0x0D
        && bytes[5] == 0x0A
        && bytes[6] == 0x1A
        && bytes[7] == 0x0A;
  }

  private static boolean isJpeg(final byte[] bytes) {
    return bytes.length >= 3
        && bytes[0] == (byte) 0xFF
        && bytes[1] == (byte) 0xD8
        && bytes[2] == (byte) 0xFF;
  }

  private static boolean isSvg(final byte[] bytes) {
    final String head =
        new String(bytes, 0, Math.min(bytes.length, 256), StandardCharsets.UTF_8).stripLeading();
    return CI.startsWith(head, "<svg") || CI.startsWith(head, "<?xml");
  }

  private static void rejectUnsafeSvgMarkup(final byte[] bytes, final String context) {
    final String head =
        new String(bytes, 0, Math.min(bytes.length, SVG_MARKUP_SCAN_BYTES), StandardCharsets.UTF_8)
            .toLowerCase(Locale.ROOT);
    if (head.contains("<!doctype") || head.contains("<!entity")) {
      throw new IllegalStateException(
          "SVG in " + context + " must not declare a DTD or external entities");
    }
    if (head.contains("<script")) {
      throw new IllegalStateException("SVG in " + context + " must not contain scripts");
    }
  }

  public static BufferedImage loadImage(final String resourcePath) {
    try (final InputStream stream = Objects.requireNonNull(
        ImageResourceLoader.class.getResourceAsStream(resourcePath))) {
      return ImageIO.read(stream);
    } catch (Exception ex) {
      throw new RuntimeException("Can't load image " + resourcePath, ex);
    }
  }

  public static Icon loadIcon(final String resourcePath, final int width, final int height) {
    final String cacheKey = resourcePath + '\0' + width + 'x' + height;
    return ImageResourceLoader.ICON_CACHE.computeIfAbsent(
        cacheKey, key -> ImageResourceLoader.loadIconUncached(resourcePath, width, height));
  }

  private static Icon loadIconUncached(
      final String resourcePath, final int width, final int height) {
    if (CI.endsWith(resourcePath, ".svg")) {
      return ImageResourceLoader.buildSvgIcon(resourcePath, width, height);
    }
    final URL resource = ImageResourceLoader.class.getResource(resourcePath);
    if (resource == null) {
      throw new IllegalArgumentException("Resource is not found: " + resourcePath);
    }
    try {
      final BufferedImage original = ImageIO.read(resource);
      if (original == null) {
        throw new IllegalStateException("Unsupported or empty image: " + resourcePath);
      }
      return new ImageIcon(scaleImage(original, width, height));
    } catch (Exception ex) {
      throw new IllegalStateException("Can't load raster image: " + resourcePath, ex);
    }
  }

  private static BufferedImage scaleImage(
      final BufferedImage original, final int width, final int height) {
    final BufferedImage scaled =
        new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    final Graphics2D graphics = scaled.createGraphics();
    try {
      ImageResourceLoader.applyHighQualityDrawingHints(graphics);
      graphics.drawImage(original, 0, 0, width, height, null);
      return scaled;
    } finally {
      graphics.dispose();
    }
  }

  private static BufferedImage scaleImageToFit(
      final BufferedImage original, final int maxWidth, final int maxHeight) {
    requirePositiveSize(maxWidth, maxHeight);
    final double scale = Math.min(1.0d, Math.min(
        (double) maxWidth / original.getWidth(),
        (double) maxHeight / original.getHeight()));
    final int width = Math.max(1, (int) Math.round(original.getWidth() * scale));
    final int height = Math.max(1, (int) Math.round(original.getHeight() * scale));
    return scaleImage(original, width, height);
  }

  private static void requirePositiveSize(final int width, final int height) {
    if (width <= 0 || height <= 0) {
      throw new IllegalArgumentException("Image bounds must be positive: " + width + "x" + height);
    }
  }

  /**
   * Logical UI scale of the default screen (e.g. 2.0 on HiDPI). Used so SVG icons rasterize with
   * enough pixels for {@link BaseMultiResolutionImage} / {@link ImageIcon} HiDPI selection.
   */
  private static double defaultScreenScale() {
    if (GraphicsEnvironment.isHeadless()) {
      return 1.0d;
    }
    final AffineTransform transform =
        GraphicsEnvironment.getLocalGraphicsEnvironment()
            .getDefaultScreenDevice()
            .getDefaultConfiguration()
            .getDefaultTransform();
    return Math.max(transform.getScaleX(), transform.getScaleY());
  }

  private static Icon buildSvgIcon(final String resourcePath, final int width, final int height) {
    final BufferedImage base = ImageResourceLoader.loadSvgImage(resourcePath, width, height);
    final double scale = ImageResourceLoader.defaultScreenScale();
    if (scale <= 1.001d) {
      return new ImageIcon(base);
    }
    final int hiW = (int) Math.ceil(width * scale);
    final int hiH = (int) Math.ceil(height * scale);
    if (hiW <= width && hiH <= height) {
      return new ImageIcon(base);
    }
    final BufferedImage hi = ImageResourceLoader.loadSvgImage(resourcePath, hiW, hiH);
    final Image multi = new BaseMultiResolutionImage(0, base, hi);
    return new ImageIcon(multi);
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
    final URL resource = ImageResourceLoader.class.getResource(resourcePath);
    if (resource == null) {
      throw new IllegalArgumentException("Resource is not found: " + resourcePath);
    }
    try {
      final PNGTranscoder transcoder = secureSvgTranscoder(width, height);
      final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      final TranscoderOutput output = new TranscoderOutput(outputStream);
      try (final InputStream stream = resource.openStream()) {
        transcoder.transcode(new TranscoderInput(stream), output);
      }
      outputStream.flush();
      return ImageIO.read(new ByteArrayInputStream(outputStream.toByteArray()));
    } catch (Exception ex) {
      throw new IllegalStateException("Can't load SVG image: " + resourcePath, ex);
    }
  }

  private static PNGTranscoder secureEmbeddedSvgTranscoder() {
    final PNGTranscoder transcoder = highQualityPngTranscoder();
    configureSecureSvgTranscoderHints(transcoder);
    transcoder.addTranscodingHint(PNGTranscoder.KEY_WIDTH, (float) EMBEDDED_SVG_RASTER_WIDTH);
    transcoder.addTranscodingHint(
        SVGAbstractTranscoder.KEY_MAX_WIDTH, (float) EMBEDDED_SVG_RASTER_WIDTH);
    transcoder.addTranscodingHint(
        SVGAbstractTranscoder.KEY_MAX_HEIGHT, (float) EMBEDDED_SVG_RASTER_MAX_HEIGHT);
    return transcoder;
  }

  private static PNGTranscoder secureSvgTranscoder(final int width, final int height) {
    final PNGTranscoder transcoder = highQualityPngTranscoder();
    configureSecureSvgTranscoderHints(transcoder);
    transcoder.addTranscodingHint(PNGTranscoder.KEY_WIDTH, (float) width);
    transcoder.addTranscodingHint(PNGTranscoder.KEY_HEIGHT, (float) height);
    transcoder.addTranscodingHint(SVGAbstractTranscoder.KEY_MAX_WIDTH, (float) width);
    transcoder.addTranscodingHint(SVGAbstractTranscoder.KEY_MAX_HEIGHT, (float) height);
    return transcoder;
  }

  private static void configureSecureSvgTranscoderHints(final PNGTranscoder transcoder) {
    transcoder.addTranscodingHint(SVGAbstractTranscoder.KEY_ALLOW_EXTERNAL_RESOURCES,
        Boolean.FALSE);
    transcoder.addTranscodingHint(SVGAbstractTranscoder.KEY_EXECUTE_ONLOAD, Boolean.FALSE);
    transcoder.addTranscodingHint(
        SVGAbstractTranscoder.KEY_ALLOWED_SCRIPT_TYPES, DISALLOWED_SVG_SCRIPT_TYPES);
    transcoder.addTranscodingHint(SVGAbstractTranscoder.KEY_CONSTRAIN_SCRIPT_ORIGIN, Boolean.TRUE);
  }

  private static PNGTranscoder highQualityPngTranscoder() {
    return new PNGTranscoder() {
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
  }
}
