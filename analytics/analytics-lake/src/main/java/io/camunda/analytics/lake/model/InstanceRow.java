/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.model;

/**
 * One finished process instance — the "case table" row, written exactly once, at the moment the
 * instance completes or terminates.
 *
 * <p>{@code varsJson} is the tier-2 variable payload: a JSON object of the instance's
 * <b>root-scope</b> variables with <b>last-value-wins</b> semantics (the values visible at
 * completion time). Subprocess-/element-scoped variables are deliberately absent — that is the
 * documented rule, not an omission.
 *
 * @param key the process instance key
 * @param processDefinitionKey the deployed definition's key
 * @param processId the BPMN process id
 * @param version the definition version
 * @param tenantId the tenant, {@code <default>} when untenanted
 * @param state {@code COMPLETED} or {@code TERMINATED}
 * @param startMs epoch millis of instance activation
 * @param endMs epoch millis of instance completion/termination
 * @param durationMs {@code endMs - startMs}
 * @param varsJson root-scope variables as one JSON object string, {@code {}} when none
 */
public record InstanceRow(
    long key,
    long processDefinitionKey,
    String processId,
    int version,
    String tenantId,
    String state,
    long startMs,
    long endMs,
    long durationMs,
    String varsJson) {}
