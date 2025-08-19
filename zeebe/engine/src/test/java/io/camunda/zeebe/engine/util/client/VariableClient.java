/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util.client;

import io.camunda.zeebe.protocol.impl.record.value.variable.VariableRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.test.util.MsgPackUtil;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.Random;
import java.util.function.LongFunction;
import org.agrona.concurrent.UnsafeBuffer;

public final class VariableClient {

  private static final long DEFAULT_KEY = -1;
  private static final LongFunction<Record<VariableRecordValue>> SUCCESS_SUPPLIER =
      (sourceRecordPosition) ->
          RecordingExporter.variableRecords()
              .onlyEvents()
              .withSourceRecordPosition(sourceRecordPosition)
              .getFirst();
  private static final LongFunction<Record<VariableRecordValue>> REJECTION_SUPPLIER =
      (sourceRecordPosition) ->
          RecordingExporter.variableRecords()
              .onlyCommandRejections()
              .withSourceRecordPosition(sourceRecordPosition)
              .getFirst();
  private long key;
  private final VariableRecord variableRecord;
  private final CommandWriter writer;
  private final String[] authorizedTenants = new String[] {TenantOwned.DEFAULT_TENANT_IDENTIFIER};

  private final long requestId = new Random().nextLong();
  private final int requestStreamId = new Random().nextInt();

  private LongFunction<Record<VariableRecordValue>> expectation = SUCCESS_SUPPLIER;

  public VariableClient(final CommandWriter writer) {
    this.writer = writer;
    variableRecord = new VariableRecord();
  }

  public VariableClient withClusterVariable(final String name, final Object value) {
    variableRecord.setScopeKey(-1);
    variableRecord.setProcessInstanceKey(-1);
    variableRecord.setProcessDefinitionKey(-1);
    variableRecord.setName(name);
    variableRecord.setValue(new UnsafeBuffer(MsgPackUtil.asMsgPack(value)));
    return this;
  }

  public VariableClient withKey(final long key) {
    this.key = key;
    return this;
  }

  public VariableClient withValue(final Object value) {
    variableRecord.setValue(new UnsafeBuffer(MsgPackUtil.asMsgPack(value)));
    return this;
  }

  public VariableClient expectRejection() {
    expectation = REJECTION_SUPPLIER;
    return this;
  }

  public Record<VariableRecordValue> create() {
    final long position =
        writer.writeCommand(
            DEFAULT_KEY,
            requestStreamId,
            requestId,
            VariableIntent.CREATE,
            variableRecord,
            authorizedTenants);
    return expectation.apply(position);
  }

  public Record<VariableRecordValue> update() {
    final long position =
        writer.writeCommand(
            key,
            requestStreamId,
            requestId,
            VariableIntent.UPDATE,
            variableRecord,
            authorizedTenants);
    return expectation.apply(position);
  }

  public Record<VariableRecordValue> delete() {
    final long position =
        writer.writeCommand(
            key,
            requestStreamId,
            requestId,
            VariableIntent.DELETE,
            variableRecord,
            authorizedTenants);
    return expectation.apply(position);
  }
}
