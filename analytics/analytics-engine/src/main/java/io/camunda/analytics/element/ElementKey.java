/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.element;

/** Heatmap grouping key: a single element within a process definition. */
public record ElementKey(
    String bpmnProcessId,
    long processDefinitionKey,
    int version,
    String tenantId,
    String elementId,
    String elementType) {}
