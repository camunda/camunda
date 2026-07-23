/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.web;

import io.camunda.analytics.lake.serving.tools.CohortShareQuery;
import io.camunda.analytics.lake.serving.tools.CohortShareService;
import io.camunda.analytics.lake.serving.tools.GaugeSeriesQuery;
import io.camunda.analytics.lake.serving.tools.GaugeSeriesService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Two more {@code POST /api/tools/*} endpoints that don't fit {@link ToolsController}'s
 * metrics-registry-entity shape: {@code gauge-series} reads a plain periodic sample table with no
 * {@code _metrics}/{@code _hist} pair behind it ({@link GaugeSeriesService}), and {@code
 * cohort-share} answers a share-below-threshold question {@link ToolsController}'s {@code
 * decompose}/{@code series} tools have no primitive for ({@link CohortShareService}). Kept as their
 * own controller rather than folded into {@link ToolsController} since neither shares that
 * controller's per-entity request/response plumbing.
 */
@RestController
@RequestMapping("/api/tools")
public class GaugeController {

  private final GaugeSeriesService gaugeSeriesService;
  private final CohortShareService cohortShareService;

  public GaugeController(
      final GaugeSeriesService gaugeSeriesService, final CohortShareService cohortShareService) {
    this.gaugeSeriesService = gaugeSeriesService;
    this.cohortShareService = cohortShareService;
  }

  @PostMapping("/gauge-series")
  public Map<String, Object> gaugeSeries(@RequestBody final GaugeSeriesQuery request) {
    final var result = gaugeSeriesService.series(request);
    return response(request, result.sql(), Map.of("points", result.points()));
  }

  @PostMapping("/cohort-share")
  public Map<String, Object> cohortShare(@RequestBody final CohortShareQuery request) {
    final var result = cohortShareService.share(request);
    return response(request, result.sql(), Map.of("series", result.series()));
  }

  private Map<String, Object> response(
      final Object params, final List<String> sql, final Map<String, Object> body) {
    final Map<String, Object> response = new LinkedHashMap<>(body);
    response.put("sql", sql);
    response.put("params", params);
    return response;
  }

  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Map<String, String> onInvalid(final IllegalArgumentException e) {
    return Map.of("error", e.getMessage() == null ? "invalid request" : e.getMessage());
  }

  @ExceptionHandler(IllegalStateException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Map<String, String> onQueryFailure(final IllegalStateException e) {
    return Map.of("error", e.getMessage() == null ? "query failed" : e.getMessage());
  }
}
