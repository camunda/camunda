/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.metric;

/**
 * Grouping key for process-instance execution time by region (and process coordinates). A purely
 * domain concept — the streaming library only sees it as an opaque, value-equal key.
 */
public record RegionKey(
    String region, String bpmnProcessId, long processDefinitionKey, int version, String tenantId) {}
