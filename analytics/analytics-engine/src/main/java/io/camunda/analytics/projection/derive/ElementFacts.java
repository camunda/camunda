/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.projection.derive;

import io.camunda.analytics.dimension.Utf8View;
import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.fact.Transition;
import io.camunda.analytics.projection.SourceRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;

/**
 * The structural fields shared by the element/process-instance facts (activation and completion).
 * String-typed fields ride as UTF-8 views over the record's own buffers (ADR 0008); tenantId has no
 * buffer getter on the record, so it stays a {@code String}.
 */
final class ElementFacts {

  private ElementFacts() {}

  static Fact.Builder base(
      final SourceRecord source, final ProcessInstanceRecord value, final Transition transition) {
    final boolean isProcess = value.getBpmnElementType() == BpmnElementType.PROCESS;
    return Fact.builder(isProcess ? FactType.PROCESS_INSTANCE : FactType.ELEMENT)
        .eventTime(source.record().getTimestamp())
        .source(source.partitionId(), source.offset())
        .transition(transition)
        .field("bpmnProcessId", Utf8View.copyOf(value.getBpmnProcessIdBuffer()))
        .field("processDefinitionKey", value.getProcessDefinitionKey())
        // Uniform on PI and ELEMENT facts: keys tables (open instances) and feeds the rework
        // approximation (distinct instances per element).
        .field("processInstanceKey", value.getProcessInstanceKey())
        .field("version", value.getVersion())
        .field("tenantId", value.getTenantId())
        // The ±1 lifecycle delta (the incident facts' convention): a LEVEL meter summing it is
        // the currently-active gauge, and its periodic snapshots are "active over time".
        .field("delta", transition == Transition.ACTIVATED ? 1L : -1L);
  }
}
