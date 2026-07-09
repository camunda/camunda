/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.serving.spi;

/**
 * The monotone fence a serving write carries: the writer's coordinator {@code epoch} (the ownership
 * generation under which it holds the partition — see the runtime's {@code OwnershipEpoch}) and the
 * commit-cut {@code offset} the write reflects. Compared lexicographically — a higher epoch always
 * wins (a later owner supersedes any former owner regardless of offsets, which are not comparable
 * across writers), and within one epoch a higher offset wins (one writer's cuts are sequential).
 *
 * <p>A backend applies a write only when its version is {@code >=} the stored one (equal accepted —
 * a deterministic replay re-writes idempotently), so a fenced zombie's stale overwrite is rejected
 * by the store itself and served values can never regress. Rejection is success: the row already
 * holds newer data.
 */
public record WriteVersion(long epoch, long offset) implements Comparable<WriteVersion> {

  /**
   * The version for out-of-pipeline writes (demo seeds, tests): epoch 0 predates every real
   * membership, so any pipeline write supersedes a seeded row.
   */
  public static final WriteVersion SEED = new WriteVersion(0, 0);

  /** The next version within the same ownership — for drains between commit cuts. */
  public WriteVersion next() {
    return new WriteVersion(epoch, offset + 1);
  }

  @Override
  public int compareTo(final WriteVersion other) {
    final int byEpoch = Long.compare(epoch, other.epoch);
    return byEpoch != 0 ? byEpoch : Long.compare(offset, other.offset);
  }
}
