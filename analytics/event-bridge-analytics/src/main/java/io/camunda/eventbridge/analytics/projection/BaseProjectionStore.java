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

  /**
   * Marks that {@code processInstanceKey} has raised an incident; returns {@code true} if this is
   * the first incident for the instance (so the caller can count it once for distinct metrics).
   */
  boolean markIncident(long processInstanceKey);

  /** Whether {@code processInstanceKey} has raised an incident since it started. */
  boolean hasIncident(long processInstanceKey);

  /**
   * Clears the incident flag for {@code processInstanceKey} (called when it reaches a terminal).
   */
  void clearIncident(long processInstanceKey);

  /** Records the create time of the incident open on {@code elementInstanceKey}. */
  void putIncidentStart(long elementInstanceKey, long createTimeMs);

  /**
   * Returns and removes the create time of the incident on {@code elementInstanceKey}, or {@link
   * #NO_POSITION} if none is recorded (e.g. a resolve seen without its create during replay).
   */
  long takeIncidentStart(long elementInstanceKey);

  /** The position up to which {@code partitionId} has been folded, or {@link #NO_POSITION}. */
  long getConsumedPosition(int partitionId);

  void setConsumedPosition(int partitionId, long position);

  /** The folded position of every source partition seen so far. */
  Map<Integer, Long> consumedPositions();

  /**
   * Flushes the working state (variables, element starts, incidents, consumed position) to durable
   * storage. Called at the commit interval, inside the driver's checkpoint transaction, so the base
   * projection commits atomically with the rollups and the source offset.
   */
  void checkpoint();
}
