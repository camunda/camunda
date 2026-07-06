/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dataset;

import java.util.Map;

/**
 * A dataset admitted to the {@link DatasetRegistry}: its {@code cubeId}, {@link
 * DatasetDeclaration}, {@code schemaVersion} (bumped on a forward-only grain change), and the
 * forward-only activation cutover fixed at admission. A cube is forward-only and
 * replay-deterministic because both cutovers are frozen once and read back verbatim.
 *
 * <p>Two cutovers, both immutable at admission:
 *
 * <ul>
 *   <li>{@code activationTimestampMs} — the <b>event-time cutover</b> used today: a fact belongs to
 *       the cube only when its (immutable) event time is at or after this stamp. A runtime-declared
 *       dataset freezes {@code now + debounce}; the standard bootstrapped datasets freeze {@code 0}
 *       (from the beginning of history). Event time is a property of the event, so this is
 *       replay-deterministic. See ADR 0005.
 *   <li>{@code activation} — the <b>per-source-partition activation position vector</b> (ADR 0001):
 *       the exact, out-of-order-safe cutover keyed by source coordinate. This is the proper target;
 *       it is currently always empty ({@code position >= 0}) and gates nothing on its own. A
 *       partition absent from the vector activates from its start.
 * </ul>
 *
 * <p>TODO(analytics): replace the event-time cutover with the position vector — freeze the current
 * per-source-partition source high-watermark at admission and gate purely on {@code (partition,
 * position)}. That removes the event-time edge fuzziness (out-of-order arrival / exporter clock
 * skew) and is the same knob backfill lowers. See ADR 0005 "Revisit triggers".
 */
public record RegisteredDataset(
    long cubeId,
    DatasetDeclaration declaration,
    Map<Integer, Long> activation,
    long activationTimestampMs,
    int schemaVersion) {

  public RegisteredDataset {
    activation = Map.copyOf(activation);
  }

  /**
   * Whether a fact at {@code (partition, position)} with event time {@code eventTimeMs} belongs to
   * this cube (forward-only): at/after the event-time cutover and at/after the position vector.
   */
  public boolean admits(final int partition, final long position, final long eventTimeMs) {
    return eventTimeMs >= activationTimestampMs
        && position >= activation.getOrDefault(partition, 0L);
  }
}
