/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.kafka.config;

/**
 * Policy applied when the internal publish queue is full.
 *
 * <p>{@link #BLOCK} (the default) guarantees no data loss: the Zeebe actor thread waits until the
 * background flush thread drains enough space. This propagates backpressure into the broker if
 * Kafka is persistently slow or unavailable.
 *
 * <p>The DROP_* policies avoid blocking at the cost of data loss: once a later record is
 * successfully flushed, the exported-position watermark advances past any skipped record and Zeebe
 * will never re-deliver it.
 */
public enum OverflowPolicy {
  /** Evicts the oldest queued record to make room for the incoming one. */
  DROP_OLDEST,

  /** Discards the incoming record, keeping the existing queue intact. */
  DROP_NEWEST,

  /**
   * Blocks the caller until queue space is available. No records are lost, but the Zeebe actor
   * thread is suspended for the duration.
   */
  BLOCK
}
