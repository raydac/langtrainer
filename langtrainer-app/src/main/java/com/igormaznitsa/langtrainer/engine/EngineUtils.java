package com.igormaznitsa.langtrainer.engine;

import static java.util.Objects.requireNonNull;

import java.awt.Image;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;

public final class EngineUtils {
  private EngineUtils() {

  }

  public static Image loadResourceImage(final String resource) throws IOException {
    try(final InputStream resourceStream = requireNonNull(EngineUtils.class.getResourceAsStream(resource), resource)) {
      return ImageIO.read(resourceStream);
    }
  }
}
