/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.util.client;

import io.zeebe.engine.util.StreamProcessorRule;
import io.zeebe.protocol.impl.record.value.variable.VariableDocumentRecord;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.VariableDocumentIntent;
import io.zeebe.protocol.record.value.VariableDocumentRecordValue;
import io.zeebe.protocol.record.value.VariableDocumentUpdateSemantic;
import io.zeebe.test.util.MsgPackUtil;
import io.zeebe.test.util.record.RecordingExporter;
import java.util.Map;
import org.agrona.concurrent.UnsafeBuffer;

public class VariableClient {

  private final VariableDocumentRecord variableDocumentRecord;
  private final StreamProcessorRule environmentRule;

  public VariableClient(StreamProcessorRule environmentRule) {
    this.environmentRule = environmentRule;
    variableDocumentRecord = new VariableDocumentRecord();
  }

  public VariableClient ofScope(long scopeKey) {
    variableDocumentRecord.setScopeKey(scopeKey);
    return this;
  }

  public VariableClient withDocument(Map<String, Object> variables) {
    variableDocumentRecord.setVariables(
        new UnsafeBuffer(MsgPackUtil.asMsgPack(variables).byteArray()));
    return this;
  }

  public VariableClient withUpdateSemantic(VariableDocumentUpdateSemantic semantic) {
    variableDocumentRecord.setUpdateSemantics(semantic);
    return this;
  }

  public Record<VariableDocumentRecordValue> update() {
    final long position =
        environmentRule.writeCommand(VariableDocumentIntent.UPDATE, variableDocumentRecord);

    return RecordingExporter.variableDocumentRecords()
        .withIntent(VariableDocumentIntent.UPDATED)
        .withSourceRecordPosition(position)
        .getFirst();
  }
}
