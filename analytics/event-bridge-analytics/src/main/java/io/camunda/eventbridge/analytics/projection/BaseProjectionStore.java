/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.projection;

import java.util.Map;

/**
 * The base-projection (read-model) store: per-instance variables (for enriching derived facts) plus
 * the per-source-partition position up to which the source stream has been folded. Start times —
 * for the process instance and for every element alike — live in the shared element-start store
 * (see {@code StateBackedProjectionStore#elementStarts()}), since a process instance is just the
 * root element. RocksDB serves as the local fast cache; an implementation may back it with an
 * external store. The consumed position is read back on startup to resume the source from where the
 * fold left off (start-from-offset).
 */
public interface BaseProjectionStore {

  /** Position value meaning "nothing consumed yet". */
  long NO_POSITION = -1L;

  /** The accumulated variables of {@code processInstanceKey}, or an empty map if none. */
  Map<String, String> getVariables(long processInstanceKey);

  /** Records (or overwrites) one variable of {@code processInstanceKey}. */
  void putVariable(long processInstanceKey, String name, String value);

  /** Drops all variables of {@code processInstanceKey} (called when the instance completes). */
  void deleteVariables(long processInstanceKey);

  /** The position up to which {@code partitionId} has been folded, or {@link #NO_POSITION}. */
  long getConsumedPosition(int partitionId);

  void setConsumedPosition(int partitionId, long position);

  /** The folded position of every source partition seen so far. */
  Map<Integer, Long> consumedPositions();
}
