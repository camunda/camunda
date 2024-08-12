/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.api;

import io.camunda.zeebe.stream.impl.UncontrolledStreamClock;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;

/**
 * A clock that provides the current time. Used as a marker interface instead of using {@link
 * InstantSource} directly to indicate that the clock is used for stream processing.
 */
public interface StreamClock extends InstantSource {

  static StreamClock uncontrolled(final InstantSource source) {
    return new UncontrolledStreamClock(source);
  }

  static StreamClock system() {
    return new UncontrolledStreamClock(InstantSource.system());
  }

  /**
   * A controllable {@link StreamClock} that allows to pin the time to a specific instant or to
   * offset the current system time by some duration.
   */
  interface ControllableStreamClock extends StreamClock {

    /**
     * Modifies the clock by applying the given modification. Previous modifications are overridden
     * and do not stack up.
     *
     * @param modification the modification to apply. Use {@link Modification#none()} to reset the
     *     clock to current system time.
     */
    void applyModification(Modification modification);

    /**
     * Returns the current modification applied to the clock. If no modification is applied, {@link
     * Modification.None} is returned.
     */
    Modification currentModification();

    /**
     * Shortcut to reset the clock to the current system time. Equivalent to {@code
     * applyModification(Modification.none())}.
     */
    default void reset() {
      applyModification(Modification.none());
    }

    /**
     * Shortcut to check if the clock is modified. Equivalent to {@code !(currentModification()
     * instanceof Modification.None)}
     */
    default boolean isModified() {
      return !(currentModification() instanceof Modification.None);
    }

    sealed interface Modification {
      static None none() {
        return new None();
      }

      static Pin pinAt(final Instant at) {
        return new Pin(at);
      }

      static Offset offsetBy(final Duration by) {
        return new Offset(by);
      }

      record None() implements Modification {}

      record Pin(Instant at) implements Modification {}

      record Offset(Duration by) implements Modification {
        public Offset {
          if (by.isZero()) {
            throw new IllegalArgumentException("Offset must not be zero");
          }
        }
      }
    }
  }
}
