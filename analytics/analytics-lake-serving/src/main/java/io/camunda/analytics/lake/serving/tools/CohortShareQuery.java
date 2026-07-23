/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.tools;

import java.util.List;
import java.util.Map;

/**
 * {@code POST /api/tools/cohort-share} request: for each of {@code thresholdsMs}, what share of
 * {@code entity}'s histogram mass per output bucket falls at or below that threshold -- see {@link
 * CohortShareService} for why this needs its own tool rather than reusing {@code tools/series}'s
 * quantile mode (which answers the inverse question).
 *
 * @param entity a metrics-registry entity with a histogram-backed measure (e.g. {@code
 *     instance_cohorts})
 * @param thresholdsMs one share series per threshold, in the measure's own unit (milliseconds for
 *     every {@code duration_ms} measure in this warehouse)
 * @param filters dim name -> exact-match value
 * @param from ISO-8601 inclusive start (or an epoch-milliseconds digit string)
 * @param to ISO-8601 exclusive end
 * @param grainMinutes the output bucket width, in minutes
 */
public record CohortShareQuery(
    String entity,
    List<Long> thresholdsMs,
    Map<String, Object> filters,
    String from,
    String to,
    Integer grainMinutes) {}
