/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming;

import java.util.Map;

/**
 * Durable per-partition consumed offsets, kept in the same state backend — and written in the same
 * {@link TransactionRunner transaction} — as the {@link Task} state, so that processor state and
 * the consumed offset advance as one atomic cut. On crash the runtime resumes from the last stored
 * offset, replaying (never losing) records since the last checkpoint.
 */
public interface OffsetStore {

  /**
   * The last committed offset per partition (the last <em>processed</em> position), used to resume
   * on restore. An empty map means start from the source's reset policy.
   */
  Map<Integer, Long> restore();

  /**
   * Records the last processed offset for a partition; invoked inside the checkpoint transaction.
   */
  void store(int partition, long offset);
}
