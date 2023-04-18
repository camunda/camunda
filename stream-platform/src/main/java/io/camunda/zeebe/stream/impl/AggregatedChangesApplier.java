/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.stream.impl;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.impl.rocksdb.transaction.ZeebeTransaction;
import io.camunda.zeebe.protocol.impl.record.value.management.AggregatedChangesRecord;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AggregatedChangesIntent;
import io.camunda.zeebe.stream.api.ProcessingResult;
import io.camunda.zeebe.stream.api.ProcessingResultBuilder;
import io.camunda.zeebe.stream.api.RecordProcessor;
import io.camunda.zeebe.stream.api.RecordProcessorContext;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import org.rocksdb.RocksDBException;
import org.rocksdb.WriteBatch;

public class AggregatedChangesApplier implements RecordProcessor {

  private TransactionContext context;

  @Override
  public void init(final RecordProcessorContext recordProcessorContext) {
    context = recordProcessorContext.getZeebeDb().createContext();
  }

  @Override
  public boolean accepts(final ValueType valueType) {
    return valueType == ValueType.AGGREGATED_CHANGES;
  }

  @Override
  public void replay(final TypedRecord record) {
    if (record.getRecordType() == RecordType.EVENT
        && record.getIntent() == AggregatedChangesIntent.CHANGED) {
      final var tx = (ZeebeTransaction) context.getCurrentTransaction();
      final var writeBatch =
          new WriteBatch(((AggregatedChangesRecord) record.getValue()).changes());
      try {
        tx.getRawTransaction().rebuildFromWriteBatch(writeBatch);
      } catch (RocksDBException e) {
        throw new RuntimeException(e);
      }
    }
  }

  @Override
  public ProcessingResult process(
      final TypedRecord record, final ProcessingResultBuilder processingResultBuilder) {
    return null;
  }

  @Override
  public ProcessingResult onProcessingError(
      final Throwable processingException,
      final TypedRecord record,
      final ProcessingResultBuilder processingResultBuilder) {
    return null;
  }
}
