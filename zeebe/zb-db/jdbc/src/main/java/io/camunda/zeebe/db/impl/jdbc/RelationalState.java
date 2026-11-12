/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.jdbc;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * State that the relational side of a {@link MixedZeebeDb} writes to the database after the
 * transaction committed. A snapshot of the RocksDB side must not be ahead of the database, so the
 * database waits for it before it takes a snapshot, and closes it with the database.
 */
public interface RelationalState extends AutoCloseable {

  /** Waits until the database holds every transaction committed before this call. */
  void awaitFlushed();

  /**
   * Registers the state's meters in the registry; called periodically, so it must be idempotent.
   */
  default void exportMetrics(final MeterRegistry registry) {}

  /** Writes what is not written yet and releases the resources. */
  @Override
  void close();
}
