/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.ConditionalEvaluationRecordValue;
import java.util.stream.Stream;

public final class ConditionalEvaluationRecordStream
    extends ExporterRecordWithVariablesStream<
        ConditionalEvaluationRecordValue, ConditionalEvaluationRecordStream> {

  public ConditionalEvaluationRecordStream(
      final Stream<Record<ConditionalEvaluationRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected ConditionalEvaluationRecordStream supply(
      final Stream<Record<ConditionalEvaluationRecordValue>> wrappedStream) {
    return new ConditionalEvaluationRecordStream(wrappedStream);
  }

  public ConditionalEvaluationRecordStream withProcessDefinitionKey(
      final long processDefinitionKey) {
    return valueFilter(v -> v.getProcessDefinitionKey() == processDefinitionKey);
  }

  public ConditionalEvaluationRecordStream withTenantId(final String tenantId) {
    return valueFilter(v -> tenantId.equals(v.getTenantId()));
  }
}
