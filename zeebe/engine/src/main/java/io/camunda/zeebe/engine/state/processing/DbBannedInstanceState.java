/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.processing;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.engine.Loggers;
import io.camunda.zeebe.engine.metrics.BannedInstanceMetrics;
import io.camunda.zeebe.engine.state.mutable.MutableBannedInstanceState;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceRelatedIntent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRelated;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.slf4j.Logger;

public final class DbBannedInstanceState implements MutableBannedInstanceState {

  private static final Logger LOG = Loggers.STREAM_PROCESSING;

  private static final String BAN_INSTANCE_MESSAGE =
      "Ban process instance {}, due to previous errors.";

  private final ColumnFamily<DbLong, DbNil> bannedInstanceColumnFamily;
  private final DbLong processInstanceKey;
  private final BannedInstanceMetrics bannedInstanceMetrics;

  public DbBannedInstanceState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    processInstanceKey = new DbLong();
    bannedInstanceColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.BANNED_INSTANCE,
            transactionContext,
            processInstanceKey,
            DbNil.INSTANCE);
    bannedInstanceMetrics = new BannedInstanceMetrics(zeebeDb.getMeterRegistry());
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    final var counter = new AtomicInteger(0);
    bannedInstanceColumnFamily.forEach(ignore -> counter.getAndIncrement());
    bannedInstanceMetrics.setBannedInstanceCounter(counter.get());
  }

  private void banInstance(final long key) {
    if (key >= 0) {
      LOG.warn(BAN_INSTANCE_MESSAGE, key);

      processInstanceKey.wrapLong(key);
      bannedInstanceColumnFamily.upsert(processInstanceKey, DbNil.INSTANCE);
      bannedInstanceMetrics.countBannedInstance();
    }
  }

  @Override
  public boolean isProcessInstanceBanned(final long key) {
    processInstanceKey.wrapLong(key);
    return bannedInstanceColumnFamily.exists(processInstanceKey);
  }

  @Override
  public boolean isBanned(final TypedRecord record) {
    final UnpackedObject value = record.getValue();
    if (value instanceof ProcessInstanceRelated) {
      final long processInstanceKey = ((ProcessInstanceRelated) value).getProcessInstanceKey();
      if (processInstanceKey >= 0) {
        return isProcessInstanceBanned(processInstanceKey);
      }
    }
    return false;
  }

  @Override
  public List<Long> getBannedProcessInstanceKeys() {
    final List<Long> bannedInstanceKeys = new ArrayList<>();
    bannedInstanceColumnFamily.forEach((key, nil) -> bannedInstanceKeys.add(key.getValue()));
    return bannedInstanceKeys;
  }

  @Override
  public boolean tryToBanInstance(
      final TypedRecord<?> typedRecord, final Consumer<Long> onBanningInstance) {
    final Intent intent = typedRecord.getIntent();
    if (shouldBeBanned(intent)) {
      final UnpackedObject value = typedRecord.getValue();
      if (value instanceof ProcessInstanceRelated) {
        final long processInstanceKey = ((ProcessInstanceRelated) value).getProcessInstanceKey();
        banInstance(processInstanceKey);
        onBanningInstance.accept(processInstanceKey);
      }
    }
    return false;
  }

  @Override
  public void banProcessInstance(final long processInstanceKey) {
    banInstance(processInstanceKey);
  }

  public static boolean shouldBeBanned(final Intent intent) {

    if (intent instanceof ProcessInstanceRelatedIntent) {
      final ProcessInstanceRelatedIntent processInstanceRelatedIntent =
          (ProcessInstanceRelatedIntent) intent;

      return processInstanceRelatedIntent.shouldBanInstanceOnError();
    }

    return false;
  }
}
