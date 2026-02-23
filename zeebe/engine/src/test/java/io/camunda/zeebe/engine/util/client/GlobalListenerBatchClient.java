/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util.client;

import io.camunda.zeebe.protocol.impl.record.value.globallistener.GlobalListenerBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.globallistener.GlobalListenerRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.GlobalListenerBatchIntent;
import io.camunda.zeebe.protocol.record.value.GlobalListenerBatchRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.function.LongFunction;

public final class GlobalListenerBatchClient {

  private static final LongFunction<Record<GlobalListenerBatchRecordValue>> SUCCESS_SUPPLIER =
      (sourceRecordPosition) ->
          RecordingExporter.globalListenerBatchRecords()
              .onlyEvents()
              .withSourceRecordPosition(sourceRecordPosition)
              .getFirst();

  private final GlobalListenerBatchRecord globalListenerBatchRecord;
  private final CommandWriter writer;
  private final LongFunction<Record<GlobalListenerBatchRecordValue>> expectation = SUCCESS_SUPPLIER;

  public GlobalListenerBatchClient(final CommandWriter writer) {
    globalListenerBatchRecord = new GlobalListenerBatchRecord();
    this.writer = writer;
  }

  public GlobalListenerBatchClient withListener(final GlobalListenerRecord taskListener) {
    globalListenerBatchRecord.addListener(taskListener);
    return this;
  }

  public GlobalListenerBatchClient withRecord(final GlobalListenerBatchRecord record) {
    globalListenerBatchRecord.copyFrom(record);
    return this;
  }

  public Record<GlobalListenerBatchRecordValue> configure() {
    final long position =
        writer.writeCommand(GlobalListenerBatchIntent.CONFIGURE, globalListenerBatchRecord);
    return expectation.apply(position);
  }
}
