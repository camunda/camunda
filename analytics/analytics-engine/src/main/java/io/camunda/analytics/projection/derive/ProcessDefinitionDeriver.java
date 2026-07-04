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
import io.camunda.zeebe.protocol.record.value.deployment.Process;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Emits a {@code PROCESS_DEFINITION}/{@code DEPLOYED} fact for each newly deployed process. Holds
 * no projection state — a process definition is a standalone fact, not part of an instance's fold.
 */
public final class ProcessDefinitionDeriver {

  public void deployed(final SourceRecord source, final Process process, final Consumer<Fact> out) {
    out.accept(
        Fact.builder(FactType.PROCESS_DEFINITION)
            .eventTime(source.record().getTimestamp())
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
