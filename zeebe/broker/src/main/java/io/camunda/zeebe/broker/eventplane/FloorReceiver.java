/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.eventplane;

import io.camunda.zeebe.eventplane.publisher.UploadFloor;
import java.util.concurrent.Executor;

/**
 * A follower's end of the floor's distribution: while open, it stores each floor the leader sends
 * that is ahead of its own, so that compaction stops there on this replica too, which may become
 * the leader that still needs the entries. Runs on the executor's thread, which also closes it.
 */
final class FloorReceiver implements AutoCloseable {

  private final FloorState state;
  private final FloorDistribution distribution;
  private boolean closed;

  FloorReceiver(final FloorState state, final FloorDistribution distribution) {
    this.state = state;
    this.distribution = distribution;
  }

  void open(final Executor executor) {
    distribution.subscribe(this::receive, executor);
  }

  private void receive(final UploadFloor floor) {
    // a message that came before the close may still be queued, and the state may be closed by now
    if (!closed) {
      state.advance(floor);
    }
  }

  @Override
  public void close() {
    closed = true;
    distribution.unsubscribe();
  }
}
