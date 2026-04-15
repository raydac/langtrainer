package com.igormaznitsa.langtrainer.engine;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;

public final class SoundClip implements AutoCloseable {

  private final AtomicBoolean playing = new AtomicBoolean();
  private final Clip clip;

  public SoundClip(final String resource) {
    try {
      final byte[] data = readResourceAsBytes(resource);
      this.clip = AudioSystem.getClip();
      final AudioInputStream audioStream =
          AudioSystem.getAudioInputStream(new ByteArrayInputStream(data));
      this.clip.addLineListener(event -> {
        if (event.getType() == LineEvent.Type.STOP) {
          playing.set(false);
        }
      });
      this.clip.open(audioStream);
    } catch (Exception ex) {
      throw new RuntimeException("Error during load and init sound clip: " + resource, ex);
    }
  }

  public static byte[] readResourceAsBytes(final String resource) throws IOException {
    final ByteArrayOutputStream buffer = new ByteArrayOutputStream(16384);
    final byte[] byteBuffer = new byte[16384];
    try (final InputStream is = SoundClip.class.getResourceAsStream(resource)) {
      if (is == null) {
        throw new IOException("Can't find resource: " + resource);
      }
      while (!Thread.currentThread().isInterrupted()) {
        final int read = is.read(byteBuffer);
        if (read < 0) {
          break;
        }
        if (read > 0) {
          buffer.write(byteBuffer, 0, read);
        }
      }
      return buffer.toByteArray();
    }
  }

  @Override
  public synchronized void close() {
    this.stop();
    this.clip.close();
  }

  public synchronized SoundClip play() {
    this.stop();
    if (this.playing.compareAndSet(false, true)) {
      this.clip.setFramePosition(0);
      this.clip.start();
    }
    return this;
  }


  public synchronized SoundClip play(final int loops) {
    this.stop();
    if (this.playing.compareAndSet(false, true)) {
      this.clip.setFramePosition(0);
      this.clip.setLoopPoints(0, -1);
      this.clip.loop(loops);
    }
    return this;
  }

  public synchronized SoundClip stop() {
    if (this.playing.compareAndSet(true, false)
        && this.clip.isActive()) {
      this.clip.stop();
    }
    return this;
  }

  public boolean isPlaying() {
    return this.playing.get();
  }


}
