/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.fact;

import io.camunda.analytics.dimension.FactRow;
import java.util.Objects;

/**
 * A thin {@link FactRow} adapter over {@link ProcessInstanceExecutionTimeFact}: exposes the fact's
 * structural fields by name, and falls back to the instance's {@code variables} so a variable
 * dimension such as {@code region} is read the same way — {@code get("region")} returns the
 * variable value, or {@code null} (the "unknown" bucket) when absent. This is the bridge that lets
 * the generic dimension/meter core consume the existing typed facts; the generic fact (a later
 * phase) implements {@link FactRow} natively and retires the adapter.
 */
public final class ProcessInstanceExecutionTimeFactRow implements FactRow {

  private final ProcessInstanceExecutionTimeFact fact;

  public ProcessInstanceExecutionTimeFactRow(final ProcessInstanceExecutionTimeFact fact) {
    this.fact = Objects.requireNonNull(fact, "fact");
  }

  @Override
  public Object get(final String field) {
    return switch (field) {
      case "processInstanceKey" -> fact.processInstanceKey();
      case "processDefinitionKey" -> fact.processDefinitionKey();
      case "bpmnProcessId" -> fact.bpmnProcessId();
      case "version" -> fact.version();
      case "tenantId" -> fact.tenantId();
      case "startTime" -> fact.startTime();
      case "endTime" -> fact.endTime();
      case "durationMs" -> fact.durationMs();
      case "completedNormally" -> fact.completedNormally();
      case "hadIncident" -> fact.hadIncident();
      default -> fact.variables().get(field);
    };
  }
}
