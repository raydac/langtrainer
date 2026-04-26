package com.igormaznitsa.langtrainer.modules.flygame;

import static java.util.Collections.shuffle;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Shuffles all line indices, splits them into buckets of at most {@link #BUCKET_CAPACITY}, then
 * applies a Leitner workflow per bucket: correct answers promote a line toward graduation; failures
 * return it to box 0. The next bucket is introduced only after every line in the current bucket
 * has graduated (five successful flights without demotion from the top box).
 */
final class FlyLeitnerSession {

  static final int BUCKET_CAPACITY = 7;
  /**
   * Box indices {@code 0..LEITNER_MAX_BOX}. A line graduates after a correct answer while in
   * {@code LEITNER_MAX_BOX}.
   */
  static final int LEITNER_MAX_BOX = 4;

  private final List<List<Integer>> buckets;
  private final Random random;
  private final List<Card> active = new ArrayList<>();
  private int bucketIndex;
  private boolean finished;

  FlyLeitnerSession(final int lineCount, final Random random) {
    this.random = random;
    this.buckets = this.buildBuckets(lineCount);
    this.bucketIndex = 0;
    this.finished = lineCount == 0;
    if (!this.finished) {
      this.loadBucket(0);
    }
  }

  private List<List<Integer>> buildBuckets(final int lineCount) {
    final List<Integer> ordinals = new ArrayList<>(lineCount);
    for (int i = 0; i < lineCount; i++) {
      ordinals.add(i);
    }
    shuffle(ordinals, this.random);
    final List<List<Integer>> result = new ArrayList<>();
    for (int i = 0; i < ordinals.size(); i += BUCKET_CAPACITY) {
      final int end = Math.min(i + BUCKET_CAPACITY, ordinals.size());
      result.add(new ArrayList<>(ordinals.subList(i, end)));
    }
    return result;
  }

  private void loadBucket(final int index) {
    this.active.clear();
    for (final int ord : this.buckets.get(index)) {
      this.active.add(new Card(ord));
    }
  }

  int nextOrdinalToFly() {
    if (this.finished || this.active.isEmpty()) {
      throw new IllegalStateException("No next ordinal");
    }
    int minBox = Integer.MAX_VALUE;
    for (final Card c : this.active) {
      minBox = Math.min(minBox, c.box);
    }
    final List<Card> pick = new ArrayList<>();
    for (final Card c : this.active) {
      if (c.box == minBox) {
        pick.add(c);
      }
    }
    return pick.get(this.random.nextInt(pick.size())).ordinal;
  }

  /**
   * @return {@code true} when the full list (all buckets) is completed.
   */
  boolean registerCorrect(final int ordinal) {
    if (this.finished) {
      return true;
    }
    final Card card = this.findCard(ordinal);
    if (card == null) {
      return this.finished;
    }
    if (card.box >= LEITNER_MAX_BOX) {
      this.active.remove(card);
    } else {
      card.box++;
    }
    if (this.active.isEmpty()) {
      this.bucketIndex++;
      if (this.bucketIndex >= this.buckets.size()) {
        this.finished = true;
      } else {
        this.loadBucket(this.bucketIndex);
      }
    }
    return this.finished;
  }

  void registerFailure(final int ordinal) {
    if (this.finished) {
      return;
    }
    final Card card = this.findCard(ordinal);
    if (card != null) {
      card.box = 0;
    }
  }

  int bucketCount() {
    return this.buckets.size();
  }

  int currentBucketOneBased() {
    return Math.min(this.bucketIndex + 1, Math.max(1, this.buckets.size()));
  }

  int wordsRemainingInActiveBucket() {
    return this.active.size();
  }

  boolean isFinished() {
    return this.finished;
  }

  boolean hasWorkLeft() {
    return !this.finished && !this.active.isEmpty();
  }

  private Card findCard(final int ordinal) {
    for (final Card c : this.active) {
      if (c.ordinal == ordinal) {
        return c;
      }
    }
    return null;
  }

  private static final class Card {
    final int ordinal;
    int box;

    Card(final int ordinal) {
      this.ordinal = ordinal;
      this.box = 0;
    }
  }
}
