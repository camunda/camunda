/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.state.processing;

import io.zeebe.db.ColumnFamily;
import io.zeebe.db.DbContext;
import io.zeebe.db.ZeebeDb;
import io.zeebe.db.impl.DbLong;
import io.zeebe.db.impl.DbNil;
import io.zeebe.engine.Loggers;
import io.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.zeebe.engine.state.ZbColumnFamilies;
import io.zeebe.engine.state.mutable.MutableBlackListState;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.record.intent.Intent;
import io.zeebe.protocol.record.intent.WorkflowInstanceRelatedIntent;
import io.zeebe.protocol.record.value.WorkflowInstanceRelated;
import java.util.function.Consumer;
import org.slf4j.Logger;

public final class DbBlackListState implements MutableBlackListState {

  private static final Logger LOG = Loggers.STREAM_PROCESSING;

  private static final String BLACKLIST_INSTANCE_MESSAGE =
      "Blacklist workflow instance {}, due to previous errors.";

  private final ColumnFamily<DbLong, DbNil> blackListColumnFamily;
  private final DbLong workflowInstanceKey;

  public DbBlackListState(final ZeebeDb<ZbColumnFamilies> zeebeDb, final DbContext dbContext) {
    workflowInstanceKey = new DbLong();
    blackListColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.BLACKLIST, dbContext, workflowInstanceKey, DbNil.INSTANCE);
  }

  private void blacklist(final long key) {
    if (key >= 0) {
      LOG.warn(BLACKLIST_INSTANCE_MESSAGE, workflowInstanceKey);

      workflowInstanceKey.wrapLong(key);
      blackListColumnFamily.put(workflowInstanceKey, DbNil.INSTANCE);
    }
  }

  private boolean isOnBlacklist(final long key) {
    workflowInstanceKey.wrapLong(key);
    return blackListColumnFamily.exists(workflowInstanceKey);
  }

  @Override
  public boolean isOnBlacklist(final TypedRecord record) {
    final UnpackedObject value = record.getValue();
    if (value instanceof WorkflowInstanceRelated) {
      final long workflowInstanceKey = ((WorkflowInstanceRelated) value).getWorkflowInstanceKey();
      if (workflowInstanceKey >= 0) {
        return isOnBlacklist(workflowInstanceKey);
      }
    }
    return false;
  }

  @Override
  public boolean tryToBlacklist(
      final TypedRecord<?> typedRecord, final Consumer<Long> onBlacklistingInstance) {
    final Intent intent = typedRecord.getIntent();
    if (shouldBeBlacklisted(intent)) {
      final UnpackedObject value = typedRecord.getValue();
      if (value instanceof WorkflowInstanceRelated) {
        final long workflowInstanceKey = ((WorkflowInstanceRelated) value).getWorkflowInstanceKey();
        blacklist(workflowInstanceKey);
        onBlacklistingInstance.accept(workflowInstanceKey);
      }
    }
    return false;
  }

  private boolean shouldBeBlacklisted(final Intent intent) {

    if (intent instanceof WorkflowInstanceRelatedIntent) {
      final WorkflowInstanceRelatedIntent workflowInstanceRelatedIntent =
          (WorkflowInstanceRelatedIntent) intent;

      return workflowInstanceRelatedIntent.shouldBlacklistInstanceOnError();
    }

    return false;
  }
}
