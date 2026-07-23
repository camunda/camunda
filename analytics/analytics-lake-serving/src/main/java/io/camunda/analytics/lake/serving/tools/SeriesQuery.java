/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.tools;

import java.util.Map;

/**
 * The shared "one time series" request shape reused by {@code POST /api/tools/series}, {@code
 * .../changepoint}, and (per-candidate) {@code .../screen}.
 *
 * @param measure {@code null} means the entity's bare row count ({@code cnt})
 * @param quantile {@code null} means "average" ({@code SUM(sum)/SUM(cnt)}) when {@code measure} is
 *     set; ignored when {@code measure} is {@code null}
 * @param filters dim name -> exact-match value
 * @param from ISO-8601 inclusive start
 * @param to ISO-8601 exclusive end
 * @param grainMinutes the output bucket width; must be a multiple of the entity's own stored window
 *     (checked at query time, not here)
 */
public record SeriesQuery(
    String entity,
    String measure,
    Double quantile,
    Map<String, Object> filters,
    String from,
    String to,
    Integer grainMinutes) {}
