/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.logstreams.restore;

import io.zeebe.broker.logstreams.state.StatePositionSupplier;
import io.zeebe.distributedlog.restore.snapshot.SnapshotRestoreContext;
import io.zeebe.logstreams.state.StateStorage;
import io.zeebe.util.collection.Tuple;
import java.util.function.Supplier;

public class BrokerSnapshotRestoreContext implements SnapshotRestoreContext {

  private final StatePositionSupplier positionSupplier;
  private final StateStorage restoreStateStorage;

  public BrokerSnapshotRestoreContext(
      StatePositionSupplier positionSupplier, StateStorage restoreStateStorage) {
    this.positionSupplier = positionSupplier;
    this.restoreStateStorage = restoreStateStorage;
  }

  @Override
  public StateStorage getStateStorage() {
    return this.restoreStateStorage;
  }

  @Override
  public Supplier<Tuple<Long, Long>> getSnapshotPositionSupplier() {
    return () -> positionSupplier.getLatestPositions();
  }
}
