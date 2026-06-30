/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.streaming.aggregate;

import java.util.Map;

/**
 * The durable serving store a {@link Rollup} flushes into: merges buffered partial accumulators
 * into the stored rows for their keys. The merge must use the same logic as the metric's {@link
 * AggregateFunction#merge} (an RDBMS implementation typically expresses it as an upsert; an
 * in-memory one calls {@code merge} directly).
 *
 * <p>Idempotent dedup of replayed facts (e.g. by source coordinate) is the store's responsibility,
 * since that is where durable, cross-restart de-duplication state lives.
 *
 * @param <K> the grouping key type
 * @param <ACC> the accumulator type
 */
public interface RollupStore<K, ACC> {

  /** Merges each partial into the durable row for its key. */
  void merge(Map<K, ACC> partials);
}
