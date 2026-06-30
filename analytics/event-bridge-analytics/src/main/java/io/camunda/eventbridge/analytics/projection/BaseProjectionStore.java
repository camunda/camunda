/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.projection;

import java.util.Optional;

/**
 * The base-projection (read-model) store: keyed access to {@link ProcessInstanceProjection}s plus
 * the position up to which the source stream has been folded. RocksDB serves as the local fast
 * cache; an implementation may back it with an external store. The consumed position is owned by
 * the store so it can be advanced together with the projection it reflects.
 */
public interface BaseProjectionStore {

  /** Position value meaning "nothing consumed yet". */
  long NO_POSITION = -1L;

  Optional<ProcessInstanceProjection> get(long processInstanceKey);

  void put(ProcessInstanceProjection projection);

  /** The source-stream position up to which this store has folded, or {@link #NO_POSITION}. */
  long getConsumedPosition();

  void setConsumedPosition(long position);
}
