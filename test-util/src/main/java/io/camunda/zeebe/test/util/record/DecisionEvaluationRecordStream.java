/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.DecisionEvaluationRecordValue;
import java.util.stream.Stream;

public final class DecisionEvaluationRecordStream
    extends ExporterRecordStream<DecisionEvaluationRecordValue, DecisionEvaluationRecordStream> {

  public DecisionEvaluationRecordStream(
      final Stream<Record<DecisionEvaluationRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected DecisionEvaluationRecordStream supply(
      final Stream<Record<DecisionEvaluationRecordValue>> wrappedStream) {
    return new DecisionEvaluationRecordStream(wrappedStream);
  }

  public DecisionEvaluationRecordStream withProcessInstanceKey(final long processInstanceKey) {
    return valueFilter(v -> v.getProcessInstanceKey() == processInstanceKey);
  }

  public DecisionEvaluationRecordStream withElementInstanceKey(final long elementInstanceKey) {
    return valueFilter(v -> v.getElementInstanceKey() == elementInstanceKey);
  }

  public DecisionEvaluationRecordStream withElementId(final String elementId) {
    return valueFilter(v -> v.getElementId().equals(elementId));
  }

  public DecisionEvaluationRecordStream withDecisionKey(final long decisionKey) {
    return valueFilter(v -> v.getDecisionKey() == decisionKey);
  }

  public DecisionEvaluationRecordStream withDecisionId(final String decisionId) {
    return valueFilter(v -> v.getDecisionId().equals(decisionId));
  }

  public DecisionEvaluationRecordStream withDecisionName(final String decisionName) {
    return valueFilter(v -> v.getDecisionName().equals(decisionName));
  }
}
