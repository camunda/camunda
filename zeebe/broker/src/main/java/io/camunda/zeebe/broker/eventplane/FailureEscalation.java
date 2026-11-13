/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.eventplane;

import java.time.Duration;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Tells failures that persist from those that pass, so that only the first are warned about: a
 * failure is worth a warning once attempts have failed for {@code warnAfter} without a success in
 * between, and then again every {@code warnEvery} while they keep failing. Not thread-safe; the
 * actor alone uses it.
 */
final class FailureEscalation {

  private final Duration warnAfter;
  private final Duration warnEvery;
  private @Nullable Instant failingSince;
  private @Nullable Instant lastWarned;

  FailureEscalation(final Duration warnAfter, final Duration warnEvery) {
    this.warnAfter = warnAfter;
    this.warnEvery = warnEvery;
  }

  /** Records a failed attempt, and returns whether it is worth a warning. */
  boolean failed(final Instant now) {
    if (failingSince == null) {
      failingSince = now;
    }
    final boolean warn =
        lastWarned == null
            ? !now.isBefore(failingSince.plus(warnAfter))
            : !now.isBefore(lastWarned.plus(warnEvery));
    if (warn) {
      lastWarned = now;
    }
    return warn;
  }

  /** How long attempts have failed so far, or zero if the last one succeeded. */
  Duration failingFor(final Instant now) {
    return failingSince == null ? Duration.ZERO : Duration.between(failingSince, now);
  }

  /** Forgets the failures so far, as when attempts stop for a while. */
  void reset() {
    failingSince = null;
    lastWarned = null;
  }

  /**
   * Records a successful attempt, and returns whether a warning was given about the failures before
   * it, which then deserves word that they ended.
   */
  boolean succeeded() {
    final boolean warned = lastWarned != null;
    failingSince = null;
    lastWarned = null;
    return warned;
  }
}
