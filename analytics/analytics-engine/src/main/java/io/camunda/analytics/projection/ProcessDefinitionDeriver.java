/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.projection;

import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.fact.Transition;
import io.camunda.eventbridge.streaming.fold.Collector;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.deployment.Process;
import java.nio.charset.StandardCharsets;

/**
 * Emits a {@code PROCESS_DEFINITION}/{@code DEPLOYED} fact for each newly deployed process. Holds
 * no read-model state — a process definition is a standalone fact, not part of an instance's fold.
 */
final class ProcessDefinitionDeriver implements FactDeriver {

  @Override
  public void derive(
      final SourceRecord source, final BaseProjectionStore state, final Collector<Fact> out) {
    final Record<?> record = source.record();
    if (record.getIntent() == ProcessIntent.CREATED
        && record.getValue() instanceof final Process process) {
      out.collect(
          Fact.builder(FactType.PROCESS_DEFINITION)
              .eventTime(record.getTimestamp())
              .source(source.partitionId(), source.offset())
              .transition(Transition.DEPLOYED)
              .field("bpmnProcessId", process.getBpmnProcessId())
              .field("processDefinitionKey", process.getProcessDefinitionKey())
              .field("version", process.getVersion())
              .field("tenantId", process.getTenantId())
              .field("bpmnXml", new String(process.getResource(), StandardCharsets.UTF_8))
              .build());
    }
  }
}
