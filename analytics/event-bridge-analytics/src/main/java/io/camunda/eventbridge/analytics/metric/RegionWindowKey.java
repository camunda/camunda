/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.metric;

/**
 * The grouping key for execution time grouped by region: region plus the process coordinates and
 * the event-time window the fact falls into. A record, so it is value-equal and usable as a map key
 * in the pre-aggregation buffer.
 */
public record RegionWindowKey(
    String region, long processDefinitionKey, int version, String tenantId, long windowStart) {}
