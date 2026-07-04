/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.projection.derive;

import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.fact.Transition;
import io.camunda.analytics.projection.SourceRecord;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;

/**
 * The structural fields shared by the element/process-instance facts (activation and completion).
 */
final class ElementFacts {

  private ElementFacts() {}

  static Fact.Builder base(
      final SourceRecord source,
      final ProcessInstanceRecordValue value,
      final Transition transition) {
    final boolean isProcess = value.getBpmnElementType() == BpmnElementType.PROCESS;
    return Fact.builder(isProcess ? FactType.PROCESS_INSTANCE : FactType.ELEMENT)
        .eventTime(source.record().getTimestamp())
        .source(source.partitionId(), source.offset())
        .transition(transition)
        .field("bpmnProcessId", value.getBpmnProcessId())
        .field("processDefinitionKey", value.getProcessDefinitionKey())
        .field("version", value.getVersion())
        .field("tenantId", value.getTenantId());
  }
}
