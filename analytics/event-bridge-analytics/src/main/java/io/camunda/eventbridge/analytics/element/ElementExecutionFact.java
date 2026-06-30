/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.element;

/**
 * The fact derived when a single BPMN element instance (a flow node) completes: which element, in
 * which definition, how long it took, and when it finished. Feeds the element heatmap (execution
 * count + execution time per element).
 */
public record ElementExecutionFact(
    String bpmnProcessId,
    long processDefinitionKey,
    int version,
    String tenantId,
    String elementId,
    String elementType,
    long durationMs,
    long completionTimeMs) {}
