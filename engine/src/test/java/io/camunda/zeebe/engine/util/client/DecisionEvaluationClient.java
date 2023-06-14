/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.util.client;

import io.camunda.zeebe.protocol.impl.record.value.decision.DecisionEvaluationRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.DecisionEvaluationIntent;
import io.camunda.zeebe.protocol.record.value.DecisionEvaluationRecordValue;
import io.camunda.zeebe.test.util.MsgPackUtil;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.Map;
import java.util.function.Function;

public class DecisionEvaluationClient {

  private static final Function<Long, Record<DecisionEvaluationRecordValue>> SUCCESS_EXPECTATION =
      (position) ->
          RecordingExporter.decisionEvaluationRecords()
              .withIntent(DecisionEvaluationIntent.EVALUATED)
              .withSourceRecordPosition(position)
              .getFirst();

  private static final Function<Long, Record<DecisionEvaluationRecordValue>> FAILURE_EXPECTATION =
      (position) ->
          RecordingExporter.decisionEvaluationRecords()
              .withIntent(DecisionEvaluationIntent.FAILED)
              .withSourceRecordPosition(position)
              .getFirst();

  private static final Function<Long, Record<DecisionEvaluationRecordValue>> REJECTION_EXPECTATION =
      (position) ->
          RecordingExporter.decisionEvaluationRecords()
              .onlyCommandRejections()
              .withIntent(DecisionEvaluationIntent.EVALUATE)
              .withSourceRecordPosition(position)
              .getFirst();

  private final CommandWriter writer;
  private final DecisionEvaluationRecord decisionEvaluationRecord = new DecisionEvaluationRecord();
  private Function<Long, Record<DecisionEvaluationRecordValue>> expectation = SUCCESS_EXPECTATION;

  public DecisionEvaluationClient(final CommandWriter writer) {
    this.writer = writer;
  }

  public DecisionEvaluationClient ofDecisionId(final String decisionId) {
    decisionEvaluationRecord.setDecisionId(decisionId);
    return this;
  }

  public DecisionEvaluationClient ofDecisionKey(final long decisionKey) {
    decisionEvaluationRecord.setDecisionKey(decisionKey);
    return this;
  }

  public DecisionEvaluationClient withVariables(final Map<String, Object> variables) {
    decisionEvaluationRecord.setVariables(MsgPackUtil.asMsgPack(variables));
    return this;
  }

  public DecisionEvaluationClient withVariables(final String variables) {
    decisionEvaluationRecord.setVariables(MsgPackUtil.asMsgPack(variables));
    return this;
  }

  public DecisionEvaluationClient withVariable(final String key, final Object value) {
    decisionEvaluationRecord.setVariables(MsgPackUtil.asMsgPack(key, value));
    return this;
  }

  public Record<DecisionEvaluationRecordValue> evaluate() {
    final long position =
        writer.writeCommand(DecisionEvaluationIntent.EVALUATE, decisionEvaluationRecord);

    return expectation.apply(position);
  }

  public DecisionEvaluationClient expectFailure() {
    expectation = FAILURE_EXPECTATION;
    return this;
  }

  public DecisionEvaluationClient expectRejection() {
    expectation = REJECTION_EXPECTATION;
    return this;
  }
}
