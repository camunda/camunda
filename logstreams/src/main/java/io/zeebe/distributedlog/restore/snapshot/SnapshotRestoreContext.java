/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.distributedlog.restore.snapshot;

import io.zeebe.logstreams.state.StateStorage;
import io.zeebe.util.collection.Tuple;
import java.util.function.Supplier;

public interface SnapshotRestoreContext {

  /** @return state storage of processor */
  StateStorage getStateStorage();

  /**
   * @return a supplier that supplies the minimum exported and latest processed position in the
   *     latest snapshot
   */
  Supplier<Tuple<Long, Long>> getSnapshotPositionSupplier();
}
