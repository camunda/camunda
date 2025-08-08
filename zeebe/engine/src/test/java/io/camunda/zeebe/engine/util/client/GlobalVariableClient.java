/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util.client;

import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.variable.GlobalVariableRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.GlobalVariableIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.test.util.MsgPackUtil;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.Map;
import java.util.Random;
import java.util.function.LongFunction;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class GlobalVariableClient {

  private static final long DEFAULT_KEY = -1;

  private static final LongFunction<Record<VariableRecordValue>> UPDATED_EXPECTATION_SUPPLIER =
      (sourceRecordPosition) ->
          RecordingExporter.variableRecords(VariableIntent.CREATED)
              .withSourceRecordPosition(sourceRecordPosition)
              .getFirst();

  private final GlobalVariableRecord globalVariableRecord;
  private final CommandWriter writer;

  private final long requestId = new Random().nextLong();
  private final int requestStreamId = new Random().nextInt();

  private final LongFunction<Record<VariableRecordValue>> expectation =
      UPDATED_EXPECTATION_SUPPLIER;
  private final String[] authorizedTenants = new String[] {TenantOwned.DEFAULT_TENANT_IDENTIFIER};

  public GlobalVariableClient(final CommandWriter writer) {
    this.writer = writer;
    globalVariableRecord = new GlobalVariableRecord();
  }

  public GlobalVariableClient withDocument(final Map<String, Object> variables) {
    final UnsafeBuffer serializedVariables =
        new UnsafeBuffer(MsgPackUtil.asMsgPack(variables).byteArray());
    return withDocument(serializedVariables);
  }

  public GlobalVariableClient withDocument(final DirectBuffer variables) {
    globalVariableRecord.setVariables(variables);
    return this;
  }

  public GlobalVariableClient withDocument(final String variables) {
    return withDocument(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(variables)));
  }

  public Record<VariableRecordValue> create() {
    final long position =
        writer.writeCommand(
            DEFAULT_KEY,
            requestStreamId,
            requestId,
            GlobalVariableIntent.CREATE,
            globalVariableRecord,
            authorizedTenants);
    return expectation.apply(position);
  }
}
