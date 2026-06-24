/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.operation;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.HistoryDeletionIntent;
import io.camunda.zeebe.protocol.record.value.HistoryDeletionRecordValue;
import io.camunda.zeebe.protocol.record.value.HistoryDeletionType;
import java.util.Set;

public class OperationFromHistoryDeletionHandler
    extends AbstractOperationHandler<HistoryDeletionRecordValue> {

  private static final Set<HistoryDeletionType> HANDLED_RESOURCE_TYPES =
      Set.of(HistoryDeletionType.PROCESS_INSTANCE, HistoryDeletionType.DECISION_INSTANCE);

  public OperationFromHistoryDeletionHandler(final String indexName) {
    super(indexName);
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.HISTORY_DELETION;
  }

  @Override
  public boolean handlesRecord(final Record<HistoryDeletionRecordValue> record) {
    return HistoryDeletionIntent.DELETED.equals(record.getIntent())
        && HANDLED_RESOURCE_TYPES.contains(record.getValue().getResourceType());
  }
}
