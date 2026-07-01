/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp.dashboard;

/**
 * One element's duration summary for the flow-node heatmap: how often it executed plus its average,
 * p50, p90 and max duration. Count/avg/max are merged exactly across windows; the percentiles are
 * the latest window's estimates (window percentiles do not re-aggregate in SQL).
 */
public record ElementDuration(
    String elementId,
    String elementType,
    long executedCount,
    long avgMs,
    long p50Ms,
    long p90Ms,
    long maxMs) {}
