/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp.dashboard;

import java.util.List;

/**
 * One whole dashboard render as a single response — every widget's payload computed in one {@link
 * DashboardRepository#overview} pass, so queries shared between widgets run once per render instead
 * of once per widget request. Field names mirror the client's dashboard state so the response is
 * consumed as-is.
 */
public record DashboardOverview(
    List<DurationPercentilePoint> duration,
    DurationPercentilePoint summary,
    List<RatioPoint> sla,
    List<SlaCohortPoint> slaCohorts,
    List<RatioPoint> noIncident,
    List<NoIncidentCohortPoint> noIncidentCohorts,
    List<DurationBucketPoint> durationBuckets,
    List<DistinctPoint> distinct,
    List<TopProcess> top,
    List<ElementDuration> elements,
    List<IncidentFlowNode> incidents,
    long openIncidents,
    long activeNow,
    long activated) {}
