/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.ConditionalSubscriptionRecordValue;
import java.util.List;
import java.util.stream.Stream;

public final class ConditionalSubscriptionRecordStream
    extends ExporterRecordStream<
        ConditionalSubscriptionRecordValue, ConditionalSubscriptionRecordStream> {

  public ConditionalSubscriptionRecordStream(
      final Stream<io.camunda.zeebe.protocol.record.Record<ConditionalSubscriptionRecordValue>>
          wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected ConditionalSubscriptionRecordStream supply(
      final Stream<Record<ConditionalSubscriptionRecordValue>> wrappedStream) {
    return new ConditionalSubscriptionRecordStream(wrappedStream);
  }

  public ConditionalSubscriptionRecordStream withScopeKey(final long scopeKey) {
    return valueFilter(v -> v.getScopeKey() == scopeKey);
  }

  public ConditionalSubscriptionRecordStream withElementInstanceKey(final long elementInstanceKey) {
    return valueFilter(v -> v.getElementInstanceKey() == elementInstanceKey);
  }

  public ConditionalSubscriptionRecordStream withProcessInstanceKey(final long processInstanceKey) {
    return valueFilter(v -> v.getProcessInstanceKey() == processInstanceKey);
  }

  public ConditionalSubscriptionRecordStream withProcessDefinitionKey(
      final long processDefinitionKey) {
    return valueFilter(v -> v.getProcessDefinitionKey() == processDefinitionKey);
  }

  public ConditionalSubscriptionRecordStream withBpmnProcessId(final String bpmnProcessId) {
    return valueFilter(v -> bpmnProcessId.equals(v.getBpmnProcessId()));
  }

  public ConditionalSubscriptionRecordStream withCatchEventId(final String catchEventId) {
    return valueFilter(v -> catchEventId.equals(v.getCatchEventId()));
  }

  public ConditionalSubscriptionRecordStream withCondition(final String condition) {
    return valueFilter(v -> condition.equals(v.getCondition()));
  }

  public ConditionalSubscriptionRecordStream withVariableNames(final String... variableNames) {
    return valueFilter(
        v -> {
          if (v.getVariableNames().size() != variableNames.length) {
            return false;
          }
          for (final String varName : variableNames) {
            if (!v.getVariableNames().contains(varName)) {
              return false;
            }
          }
          return true;
        });
  }

  public ConditionalSubscriptionRecordStream withVariableNames(final List<String> variableNames) {
    return valueFilter(
        v -> {
          if (v.getVariableNames().size() != variableNames.size()) {
            return false;
          }
          for (final String varName : variableNames) {
            if (!v.getVariableNames().contains(varName)) {
              return false;
            }
          }
          return true;
        });
  }

  public ConditionalSubscriptionRecordStream withVariableEvents(final String... variableEvents) {
    return valueFilter(
        v -> {
          if (v.getVariableEvents().size() != variableEvents.length) {
            return false;
          }
          for (final String varEvent : variableEvents) {
            if (!v.getVariableEvents().contains(varEvent)) {
              return false;
            }
          }
          return true;
        });
  }

  public ConditionalSubscriptionRecordStream withVariableEvents(final List<String> variableEvents) {
    return valueFilter(
        v -> {
          if (v.getVariableEvents().size() != variableEvents.size()) {
            return false;
          }
          for (final String varEvent : variableEvents) {
            if (!v.getVariableEvents().contains(varEvent)) {
              return false;
            }
          }
          return true;
        });
  }

  public ConditionalSubscriptionRecordStream isInterrupting(final boolean isInterrupting) {
    return valueFilter(v -> v.isInterrupting() == isInterrupting);
  }

  public ConditionalSubscriptionRecordStream withTenantId(final String tenantId) {
    return valueFilter(v -> tenantId.equals(v.getTenantId()));
  }
}
