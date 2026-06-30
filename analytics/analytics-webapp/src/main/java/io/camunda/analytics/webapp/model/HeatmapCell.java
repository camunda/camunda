/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp.model;

/** One element's heatmap cell: how often it executed and its execution-time stats. */
public record HeatmapCell(
    String elementId,
    String elementType,
    long executedCount,
    double averageDurationMs,
    long maxDurationMs) {}
