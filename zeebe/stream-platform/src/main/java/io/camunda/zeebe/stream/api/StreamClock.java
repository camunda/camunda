/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.api;

import io.camunda.zeebe.stream.api.StreamClock.ControllableStreamClock.Modification;
import io.camunda.zeebe.stream.impl.ControllableStreamClockImpl;
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
    return uncontrolled(InstantSource.system());
  }

  static ControllableStreamClock controllable(final InstantSource source) {
    return new ControllableStreamClockImpl(source);
  }

  /**
   * Returns the current modification applied to the clock. If no modification is applied, {@link
   * Modification.None} is returned.
   */
  Modification currentModification();

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
     * Shortcut to pin the clock to a specific instant.
     *
     * @implSpec Equivalent to {@code applyModification(Modification.pinAt(at))}.
     */
    default void pinAt(final Instant at) {
      applyModification(Modification.pinAt(at));
    }

    /**
     * Shortcut to offset the clock by a specific duration.
     *
     * @implSpec Equivalent to {@code applyModification(Modification.offsetBy(by))}.
     */
    default void offsetBy(final Duration by) {
      applyModification(Modification.offsetBy(by));
    }

    /**
     * If the clock is already offset, stacks the additional offset on top of the current offset.
     * Otherwise, offsets the clock by the given duration, overwriting any previous modification.
     */
    default void stackOffset(final Duration additionalOffset) {
      if (currentModification() instanceof Modification.Offset(final var initialOffset)) {
        applyModification(Modification.offsetBy(initialOffset.plus(additionalOffset)));
      } else {
        offsetBy(additionalOffset);
      }
    }

    /**
     * Shortcut to reset the clock to the current system time.
     *
     * @implSpec Equivalent to {@code applyModification(Modification.none())}.
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

      record Offset(Duration by) implements Modification {}
    }
  }
}
