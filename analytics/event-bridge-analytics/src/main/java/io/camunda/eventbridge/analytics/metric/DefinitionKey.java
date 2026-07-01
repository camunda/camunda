/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.metric;

/**
 * The grouping key for per-process-definition rollups (duration percentiles, SLA/no-incident
 * ratios). Optimize's default duration and percentage tiles are scoped to a process definition, so
 * this is the natural streaming grouping — one cell per definition per window.
 *
 * @param bpmnProcessId the process id
 * @param processDefinitionKey the deployed definition key
 * @param version the definition version
 * @param tenantId the tenant
 */
public record DefinitionKey(
    String bpmnProcessId, long processDefinitionKey, int version, String tenantId) {}
