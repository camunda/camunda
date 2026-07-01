/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp.dashboard;

/**
 * Incident counts for one flow node: {@code raised} = incidents created in the selected range
 * (counts incidents, so multiple per instance all count); {@code open} = currently open there
 * (created − resolved, range-independent gauge); {@code avgDurationMs}/{@code maxDurationMs} = the
 * average/longest open→resolve time of incidents resolved in the range (0 if none resolved).
 */
public record IncidentFlowNode(
    String elementId, long raised, long open, long avgDurationMs, long maxDurationMs) {}
