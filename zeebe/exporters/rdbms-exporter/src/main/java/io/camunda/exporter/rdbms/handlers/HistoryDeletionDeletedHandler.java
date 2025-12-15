/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import io.camunda.db.rdbms.write.domain.HistoryDeletionDbModel;
import io.camunda.db.rdbms.write.service.HistoryDeletionWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.HistoryDeletionIntent;
import io.camunda.zeebe.protocol.record.value.HistoryDeletionRecordValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record HistoryDeletionDeletedHandler(HistoryDeletionWriter historyDeletionWriter)
    implements RdbmsExportHandler<HistoryDeletionRecordValue> {

  private static final Logger LOGGER = LoggerFactory.getLogger(HistoryDeletionDeletedHandler.class);

  @Override
  public boolean canExport(final Record<HistoryDeletionRecordValue> record) {
    return HistoryDeletionIntent.DELETED.equals(record.getIntent())
        && ValueType.HISTORY_DELETION.equals(record.getValueType());
  }

  @Override
  public void export(final Record<HistoryDeletionRecordValue> record) {
    final HistoryDeletionRecordValue value = record.getValue();
    LOGGER.trace("Export History Deletion Record: {}", value);

    var historyDeletionDbModel =
        new HistoryDeletionDbModel.Builder()
            .resourceKey(value.getResourceKey())
            .batchOperationKey(record.getBatchOperationReference())
            .resourceType(value.getResourceType())
            .partitionId(record.getPartitionId())
            .build();
    historyDeletionWriter.create(historyDeletionDbModel);
  }
}
