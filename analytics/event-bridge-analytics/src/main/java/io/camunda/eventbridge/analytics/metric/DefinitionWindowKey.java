/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.metric;

/**
 * Grouping key for execution time by process definition (aggregated across regions): the process
 * coordinates and the event-time window. A record, so it is value-equal for use as a buffer key.
 */
public record DefinitionWindowKey(
    long processDefinitionKey, int version, String tenantId, long windowStart) {}
