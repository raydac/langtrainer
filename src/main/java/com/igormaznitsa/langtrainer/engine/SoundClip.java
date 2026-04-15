package com.igormaznitsa.langtrainer.engine;

import java.util.concurrent.atomic.AtomicBoolean;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import org.apache.commons.io.IOUtils;

public final class SoundClip implements AutoCloseable {

  private final AtomicBoolean playing = new AtomicBoolean();
  private final Clip clip;

  private static final AudioFormat FORMAT = new AudioFormat(8000.0f, 8, 1, false, false);

  public SoundClip(final String resource) {
    try {
      final byte[] data = IOUtils.resourceToByteArray(resource);
      this.clip = AudioSystem.getClip();
      this.clip.addLineListener(event -> {
        if (event.getType() == LineEvent.Type.STOP) {
          playing.set(false);
        }
      });
      this.clip.open(FORMAT, data, 0, data.length);
    } catch (Exception ex) {
      throw new RuntimeException("Error during load and init sound clip: " + resource, ex);
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
