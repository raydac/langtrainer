package com.igormaznitsa.langtrainer.modules.flygame;

import static java.util.Optional.empty;
import static java.util.Optional.of;

import com.igormaznitsa.langtrainer.engine.SoundClip;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

final class FlyGameSfx {

  static final String AIR_START = "/fly_game/wavs/airstart.wav";
  static final String EXPLOSION = "/fly_game/wavs/explosion.wav";
  static final String MISFIRE = "/fly_game/wavs/misfire.wav";
  static final String CAT_ANGRY = "/fly_game/wavs/catangry.wav";
  static final String ENDGAME = "/fly_game/wavs/endgame.wav";
  private static final Logger LOG = Logger.getLogger(FlyGameSfx.class.getName());
  private final BooleanSupplier soundEnabled;
  private final Map<String, Optional<SoundClip>> clips;

  FlyGameSfx(final BooleanSupplier soundEnabled) {
    this.soundEnabled = soundEnabled;
    final Map<String, Optional<SoundClip>> loaded = new HashMap<>();
    for (final String path : List.of(AIR_START, EXPLOSION, MISFIRE, CAT_ANGRY, ENDGAME)) {
      loaded.put(path, tryLoad(path));
    }
    this.clips = Map.copyOf(loaded);
  }

  private static Optional<SoundClip> tryLoad(final String path) {
    try {
      return of(new SoundClip(path));
    } catch (RuntimeException ex) {
      LOG.log(Level.FINE, "Fly game SFX preload failed: " + path, ex);
      return empty();
    }
  }

  void play(final String resourcePath) {
    if (!this.soundEnabled.getAsBoolean()) {
      return;
    }
    this.clips.getOrDefault(resourcePath, empty()).ifPresentOrElse(
        SoundClip::play,
        () -> LOG.log(Level.FINE, "Fly game SFX not loaded for path: " + resourcePath));
  }
}
