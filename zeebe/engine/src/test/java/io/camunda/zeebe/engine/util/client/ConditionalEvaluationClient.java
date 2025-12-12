/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util.client;

import io.camunda.zeebe.protocol.impl.record.value.conditional.ConditionalEvaluationRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ConditionalEvaluationIntent;
import io.camunda.zeebe.protocol.record.value.ConditionalEvaluationRecordValue;
import io.camunda.zeebe.test.util.MsgPackUtil;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.Map;
import java.util.function.Function;

public class ConditionalEvaluationClient {

  private static final Function<Long, Record<ConditionalEvaluationRecordValue>>
      SUCCESS_EXPECTATION =
          (position) ->
              RecordingExporter.conditionalEvaluationRecords(ConditionalEvaluationIntent.EVALUATED)
                  .withSourceRecordPosition(position)
                  .getFirst();

  private static final Function<Long, Record<ConditionalEvaluationRecordValue>>
      REJECTION_EXPECTATION =
          (position) ->
              RecordingExporter.conditionalEvaluationRecords(ConditionalEvaluationIntent.EVALUATE)
                  .onlyCommandRejections()
                  .withSourceRecordPosition(position)
                  .getFirst();

  private final CommandWriter writer;
  private final ConditionalEvaluationRecord conditionalEvaluationRecord =
      new ConditionalEvaluationRecord();
  private Function<Long, Record<ConditionalEvaluationRecordValue>> expectation =
      SUCCESS_EXPECTATION;

  public ConditionalEvaluationClient(final CommandWriter writer) {
    this.writer = writer;
  }

  public ConditionalEvaluationClient withProcessDefinitionKey(final long processDefinitionKey) {
    conditionalEvaluationRecord.setProcessDefinitionKey(processDefinitionKey);
    return this;
  }

  public ConditionalEvaluationClient withVariables(final Map<String, Object> variables) {
    conditionalEvaluationRecord.setVariables(MsgPackUtil.asMsgPack(variables));
    return this;
  }

  public ConditionalEvaluationClient withVariable(final String key, final Object value) {
    conditionalEvaluationRecord.setVariables(MsgPackUtil.asMsgPack(key, value));
    return this;
  }

  public ConditionalEvaluationClient withTenantId(final String tenantId) {
    conditionalEvaluationRecord.setTenantId(tenantId);
    return this;
  }

  public ConditionalEvaluationClient expectRejection() {
    expectation = REJECTION_EXPECTATION;
    return this;
  }

  public Record<ConditionalEvaluationRecordValue> evaluate() {
    final long position =
        writer.writeCommand(ConditionalEvaluationIntent.EVALUATE, conditionalEvaluationRecord);
    return expectation.apply(position);
  }

  public Record<ConditionalEvaluationRecordValue> evaluate(final String username) {
    final long position =
        writer.writeCommand(
            ConditionalEvaluationIntent.EVALUATE, username, conditionalEvaluationRecord);
    return expectation.apply(position);
  }
}
