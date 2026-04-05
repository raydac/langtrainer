package com.igormaznitsa.langtrainer.engine;

import java.io.BufferedInputStream;
import java.io.InputStream;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;

public final class SoundPlayer {

  public void playWav(final String resourcePath) {
    try (InputStream stream = SoundPlayer.class.getResourceAsStream(resourcePath)) {
      if (stream == null) {
        throw new IllegalArgumentException("Resource is not found: " + resourcePath);
      }
      try (AudioInputStream audioStream =
               AudioSystem.getAudioInputStream(new BufferedInputStream(stream))) {
        final Clip clip = AudioSystem.getClip();
        clip.open(audioStream);
        clip.start();
      }
    } catch (Exception ex) {
      throw new IllegalStateException("Can't play sound: " + resourcePath, ex);
    }
  }
}
