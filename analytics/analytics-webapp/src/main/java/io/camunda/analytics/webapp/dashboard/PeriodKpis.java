/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp.dashboard;

/**
 * The KPI-tile numbers of one period, aggregated over its whole range: lifecycle counts (started /
 * ended), the completed-duration distribution (count + percentiles) and the three quality ratios.
 * Two of these — the selected range and the same-length range immediately before it — form a {@link
 * KpiComparison}, the period-over-period read behind the dashboard's delta badges.
 */
public record PeriodKpis(
    long activated,
    long ended,
    DurationPercentilePoint duration,
    RatioKpi slaCompliance,
    RatioKpi noIncident,
    RatioKpi firstTimeRight) {}
