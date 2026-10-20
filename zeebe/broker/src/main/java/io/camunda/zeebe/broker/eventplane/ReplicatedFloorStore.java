/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.eventplane;

import io.camunda.zeebe.eventplane.publisher.FloorStore;
import io.camunda.zeebe.eventplane.publisher.UploadFloor;
import org.jspecify.annotations.Nullable;

/**
 * The leader's upload floor: it keeps each floor in the partition's state, and sends it to the
 * followers right away, and again on {@link #resend}. Loads and stores run on the publisher's
 * thread, with a transaction context of their own.
 */
final class ReplicatedFloorStore implements FloorStore {

  private final FloorState state;
  private final FloorDistribution distribution;
  private volatile @Nullable UploadFloor latest;

  ReplicatedFloorStore(final FloorState state, final FloorDistribution distribution) {
    this.state = state;
    this.distribution = distribution;
  }

  @Override
  public @Nullable UploadFloor load() {
    final var floor = state.floor();
    latest = floor;
    return floor;
  }

  /**
   * @throws FloorMovedBackException if the floor's position is below the stored one's
   */
  @Override
  public void store(final UploadFloor floor) {
    state.store(floor);
    latest = floor;
    distribution.send(floor);
  }

  /** Sends the latest floor loaded or stored again, for followers that missed it; on any thread. */
  void resend() {
    final var floor = latest;
    if (floor != null) {
      distribution.send(floor);
    }
  }
}
