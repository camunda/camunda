/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.aggregation;

/**
 * Where {@link EnvelopePublisher} sends encoded shuffle-envelope frames: one target facts partition
 * per frame, then a {@link #flush()} that blocks until everything sent is durable
 * (produce-before-checkpoint). A seam so the publisher's batching/chunking is testable without a
 * live event bridge; production supplies an event-bridge-backed implementation.
 */
public interface EnvelopeTransport {

  /** Buffers {@code frame} for {@code factsPartition}. */
  void send(int factsPartition, byte[] frame);

  /** Sends everything buffered and blocks until durable. */
  void flush();
}
