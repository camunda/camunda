/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util.client;

import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableDocumentRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.VariableDocumentIntent;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.VariableDocumentRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableDocumentUpdateSemantic;
import io.camunda.zeebe.test.util.MsgPackUtil;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.Map;
import java.util.Random;
import java.util.function.LongFunction;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class VariableClient {

  private static final long DEFAULT_KEY = -1;

  private static final LongFunction<Record<VariableDocumentRecordValue>>
      SUCCESSFUL_EXPECTATION_SUPPLIER =
          (sourceRecordPosition) ->
              RecordingExporter.variableDocumentRecords(VariableDocumentIntent.UPDATED)
                  .withSourceRecordPosition(sourceRecordPosition)
                  .getFirst();
  private static final LongFunction<Record<VariableDocumentRecordValue>>
      PARTIAL_SUCCESS_EXPECTATION_SUPPLIER =
          (sourceRecordPosition) ->
              RecordingExporter.variableDocumentRecords(VariableDocumentIntent.UPDATING)
                  .withSourceRecordPosition(sourceRecordPosition)
                  .getFirst();

  private static final LongFunction<Record<VariableDocumentRecordValue>>
      REJECTION_EXPECTATION_SUPPLIER =
          (sourceRecordPosition) ->
              RecordingExporter.variableDocumentRecords()
                  .onlyCommandRejections()
                  .withSourceRecordPosition(sourceRecordPosition)
                  .getFirst();

  private final VariableDocumentRecord variableDocumentRecord;
  private final CommandWriter writer;

  private long requestId = new Random().nextLong();
  private int requestStreamId = new Random().nextInt();

  private LongFunction<Record<VariableDocumentRecordValue>> expectation =
      SUCCESSFUL_EXPECTATION_SUPPLIER;
  private String[] authorizedTenants = new String[] {TenantOwned.DEFAULT_TENANT_IDENTIFIER};

  public VariableClient(final CommandWriter writer) {
    this.writer = writer;
    variableDocumentRecord = new VariableDocumentRecord();
  }

  public VariableClient ofScope(final long scopeKey) {
    variableDocumentRecord.setScopeKey(scopeKey);
    return this;
  }

  public VariableClient withDocument(final Map<String, Object> variables) {
    final UnsafeBuffer serializedVariables =
        new UnsafeBuffer(MsgPackUtil.asMsgPack(variables).byteArray());
    return withDocument(serializedVariables);
  }

  public VariableClient withDocument(final DirectBuffer variables) {
    variableDocumentRecord.setVariables(variables);
    return this;
  }

  public VariableClient withDocument(final String variables) {
    return withDocument(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(variables)));
  }

  public VariableClient withUpdateSemantic(final VariableDocumentUpdateSemantic semantic) {
    variableDocumentRecord.setUpdateSemantics(semantic);
    return this;
  }

  public VariableClient forAuthorizedTenants(final String... authorizedTenants) {
    this.authorizedTenants = authorizedTenants;
    return this;
  }

  public VariableClient expectRejection() {
    expectation = REJECTION_EXPECTATION_SUPPLIER;
    return this;
  }

  public VariableClient expectPartialUpdate() {
    expectation = PARTIAL_SUCCESS_EXPECTATION_SUPPLIER;
    return this;
  }

  public Record<VariableDocumentRecordValue> update() {
    final long position =
        writer.writeCommand(
            DEFAULT_KEY,
            requestStreamId,
            requestId,
            VariableDocumentIntent.UPDATE,
            variableDocumentRecord,
            authorizedTenants);
    return expectation.apply(position);
  }

  public Record<VariableDocumentRecordValue> update(final String username) {
    final long position =
        writer.writeCommand(
            DEFAULT_KEY,
            requestStreamId,
            requestId,
            VariableDocumentIntent.UPDATE,
            username,
            variableDocumentRecord,
            authorizedTenants);
    return expectation.apply(position);
  }
}
