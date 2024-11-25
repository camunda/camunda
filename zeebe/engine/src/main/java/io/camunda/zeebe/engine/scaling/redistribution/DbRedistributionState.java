/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.scaling.redistribution;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.scaling.RedistributionProgress;

public final class DbRedistributionState implements MutableRedistributionState {
  private final ColumnFamily<DbString, PersistedState> redistributionColumnFamily;
  private final DbString key = new DbString();

  public DbRedistributionState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    key.wrapString("redistributionState");
    redistributionColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.REDISTRIBUTION, transactionContext, key, new PersistedState());
  }

  @Override
  public RedistributionStage getStage() {
    final var persistedState = redistributionColumnFamily.get(key);
    return persistedState == null
        ? new RedistributionStage.Done()
        : RedistributionStage.indexToStage(persistedState.stage.getValue());
  }

  @Override
  public RedistributionProgress getProgress() {
    final var persistedState = redistributionColumnFamily.get(key);
    return persistedState == null
        ? new RedistributionProgress()
        : persistedState.progress.getValue();
  }

  @Override
  public void initializeState(
      final RedistributionStage stage, final RedistributionProgress progress) {
    final var persistedState = new PersistedState();
    persistedState.stage.setValue(RedistributionStage.stageToIndex(stage));
    persistedState.progress.getValue().copyFrom(progress);
    redistributionColumnFamily.insert(key, persistedState);
  }

  @Override
  public void updateState(final RedistributionStage stage, final RedistributionProgress progress) {
    final var persistedState = redistributionColumnFamily.get(key);
    persistedState.stage.setValue(RedistributionStage.stageToIndex(stage));
    persistedState.progress.getValue().copyFrom(progress);
    redistributionColumnFamily.update(key, persistedState);
  }

  @Override
  public void clearState() {
    redistributionColumnFamily.deleteExisting(key);
  }

  private static final class PersistedState extends UnpackedObject implements DbValue {
    private final IntegerProperty stage =
        new IntegerProperty(
            "stage", RedistributionStage.stageToIndex(new RedistributionStage.Done()));

    private final ObjectProperty<RedistributionProgress> progress =
        new ObjectProperty<>("progress", new RedistributionProgress());

    public PersistedState() {
      super(2);
      declareProperty(stage);
      declareProperty(progress);
    }
  }
}
