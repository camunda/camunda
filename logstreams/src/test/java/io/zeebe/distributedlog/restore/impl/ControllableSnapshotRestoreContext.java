/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.distributedlog.restore.impl;

import io.zeebe.distributedlog.restore.snapshot.SnapshotRestoreContext;
import io.zeebe.logstreams.state.StateStorage;
import io.zeebe.util.collection.Tuple;
import java.util.function.Supplier;

public class ControllableSnapshotRestoreContext implements SnapshotRestoreContext {

  private StateStorage processorStateStorage;
  private Supplier<Tuple<Long, Long>> positionSupplier;

  public void setProcessorStateStorage(StateStorage processorStateStorage) {
    this.processorStateStorage = processorStateStorage;
  }

  public void setPositionSupplier(Supplier<Tuple<Long, Long>> exporterPositionSupplier) {
    this.positionSupplier = exporterPositionSupplier;
  }

  @Override
  public StateStorage getStateStorage() {
    return processorStateStorage;
  }

  @Override
  public Supplier<Tuple<Long, Long>> getSnapshotPositionSupplier() {
    return positionSupplier;
  }

  public void reset() {
    processorStateStorage = null;
    positionSupplier = null;
  }
}
