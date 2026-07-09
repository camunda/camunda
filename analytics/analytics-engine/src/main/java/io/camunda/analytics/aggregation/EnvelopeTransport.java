/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.aggregation;

import java.util.concurrent.CompletableFuture;

/**
 * Where {@link EnvelopePublisher} sends encoded shuffle-envelope frames: one target facts partition
 * per frame, handed over without waiting, then a {@link #dispatch()} that starts publishing
 * everything buffered and returns the acknowledgment future (produce-before-checkpoint awaits it
 * once, so the wait is the slowest destination's round trip, not the sum). A seam so the
 * publisher's batching/chunking is testable without a live event bridge; production supplies an
 * event-bridge-backed implementation.
 *
 * <p><b>Ordering contract.</b> The reduce-side segment dedup is a strictly-monotonic {@code
 * (segment, chunk)} watermark per stream — a frame arriving behind a newer one is dropped as a
 * re-emit, which loses data rather than deduping it. An implementation must therefore preserve
 * per-destination arrival order: frames {@link #send(int, byte[]) sent} to the same facts partition
 * arrive in hand-over order, both within one dispatch and across successive dispatches. Distinct
 * facts partitions are independent and may be published concurrently.
 */
public interface EnvelopeTransport {

  /** Buffers {@code frame} for {@code factsPartition}; never blocks and never does IO. */
  void send(int factsPartition, byte[] frame);

  /**
   * Starts publishing every buffered frame — pipelined: all destinations at once, without waiting
   * in between — and returns a future that completes when all of them are durably acknowledged (or
   * exceptionally when any publish fails). Must uphold the per-destination ordering contract of
   * this interface across successive dispatches.
   */
  CompletableFuture<Void> dispatch();

  /** Publishes everything buffered and blocks until durable. */
  default void flush() {
    dispatch().join();
  }
}
