/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.planner;

import io.camunda.analytics.lake.serving.tools.ExemplarsService.ExemplarRow;
import java.util.List;
import java.util.Map;

/**
 * One {@code POST /api/investigate} finding. {@code kind} is always one of {@code CHANGEPOINT},
 * {@code DIMENSION_DRIVER}, {@code SCREEN_CORRELATION}, {@code COHORT_ATTRIBUTE}, or {@code
 * SCAN_DEFERRED} (pinned by the UI lane's contract addendum) — {@code claim} holds exactly the
 * field names each kind's UI phrase template reads (see {@link InvestigateService}'s javadoc for
 * the per-kind list); {@code numbers} repeats the claim's own numeric fields for callers that want
 * only the numbers, not the full claim shape. {@code toolParams}, together with {@code tool}, is
 * always enough to re-run the exact query that produced this finding via {@code POST
 * /api/tools/<tool>}. {@code exemplars} is non-null only on the single top-ranked {@code
 * COHORT_ATTRIBUTE} finding (see {@link InvestigateService}'s rung-4 step).
 */
public record Finding(
    String id,
    int rung,
    String kind,
    Map<String, Object> claim,
    Map<String, Object> numbers,
    double support,
    double effect,
    String tool,
    Object toolParams,
    List<String> sql,
    List<ExemplarRow> exemplars) {}
