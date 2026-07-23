/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.web;

import io.camunda.analytics.lake.serving.tools.ChangepointService;
import io.camunda.analytics.lake.serving.tools.CohortCompareQuery;
import io.camunda.analytics.lake.serving.tools.CohortCompareService;
import io.camunda.analytics.lake.serving.tools.CohortSpec;
import io.camunda.analytics.lake.serving.tools.ConditionsQuery;
import io.camunda.analytics.lake.serving.tools.ConditionsService;
import io.camunda.analytics.lake.serving.tools.DecomposeQuery;
import io.camunda.analytics.lake.serving.tools.DecomposeService;
import io.camunda.analytics.lake.serving.tools.ExemplarsQuery;
import io.camunda.analytics.lake.serving.tools.ExemplarsService;
import io.camunda.analytics.lake.serving.tools.ScreenService;
import io.camunda.analytics.lake.serving.tools.SeriesQuery;
import io.camunda.analytics.lake.serving.tools.SeriesService;
import io.camunda.analytics.lake.serving.tools.TimeRange;
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
import tools.jackson.databind.ObjectMapper;

/**
 * The explain tool endpoints ({@code POST /api/tools/*}) — see this module's README for the exact
 * request/response contract each one follows. Every response is assembled as a plain {@code
 * LinkedHashMap} (rather than a dedicated response record per endpoint) so it can uniformly carry
 * the endpoint's own result fields alongside the two contract-mandated ones every tool response
 * includes: {@code sql} (every statement executed) and {@code params} (the request echoed back).
 */
@RestController
@RequestMapping("/api/tools")
public class ToolsController {

  private final SeriesService seriesService;
  private final ChangepointService changepointService;
  private final DecomposeService decomposeService;
  private final ScreenService screenService;
  private final CohortCompareService cohortCompareService;
  private final ExemplarsService exemplarsService;
  private final ConditionsService conditionsService;
  private final ObjectMapper objectMapper;

  public ToolsController(
      final SeriesService seriesService,
      final ChangepointService changepointService,
      final DecomposeService decomposeService,
      final ScreenService screenService,
      final CohortCompareService cohortCompareService,
      final ExemplarsService exemplarsService,
      final ConditionsService conditionsService,
      final ObjectMapper objectMapper) {
    this.seriesService = seriesService;
    this.changepointService = changepointService;
    this.decomposeService = decomposeService;
    this.screenService = screenService;
    this.cohortCompareService = cohortCompareService;
    this.exemplarsService = exemplarsService;
    this.conditionsService = conditionsService;
    this.objectMapper = objectMapper;
  }

  @PostMapping("/series")
  public Map<String, Object> series(@RequestBody final SeriesQuery request) {
    final var result = seriesService.series(request);
    return response(request, result.sql(), Map.of("points", result.points()));
  }

  @PostMapping("/changepoint")
  public Map<String, Object> changepoint(@RequestBody final SeriesQuery request) {
    final var result = changepointService.changepoint(request);
    final Map<String, Object> body = new LinkedHashMap<>();
    body.put("at", result.at());
    body.put("shape", result.shape());
    body.put("confidence", result.confidence());
    body.put("before", result.before());
    body.put("after", result.after());
    return response(request, result.sql(), body);
  }

  @PostMapping("/decompose")
  public Map<String, Object> decompose(@RequestBody final DecomposeQuery request) {
    final var result = decomposeService.decompose(request);
    return response(request, result.sql(), Map.of("rows", result.rows()));
  }

  @PostMapping("/screen")
  public Map<String, Object> screen(@RequestBody final ScreenRequest request) {
    final List<SeriesQuery> explicitCandidates = resolveScreenCandidates(request.candidates());
    final var result =
        screenService.screen(request.targetSeries(), request.window(), explicitCandidates);
    return response(request, result.sql(), Map.of("rows", result.rows()));
  }

  @PostMapping("/cohort-compare")
  public Map<String, Object> cohortCompare(@RequestBody final CohortCompareRequest request) {
    final List<String> explicitAttributes = resolveAttributes(request.attributes());
    final CohortCompareQuery query =
        new CohortCompareQuery(
            request.entity(),
            request.cohort(),
            request.filters(),
            request.from(),
            request.to(),
            explicitAttributes,
            request.supportFloor());
    final var result = cohortCompareService.compare(query);
    return response(request, result.sql(), Map.of("rows", result.rows()));
  }

  @PostMapping("/exemplars")
  public Map<String, Object> exemplars(@RequestBody final ExemplarsQuery request) {
    final var result = exemplarsService.exemplars(request);
    return response(request, result.sql(), Map.of("rows", result.rows()));
  }

  @PostMapping("/conditions")
  public Map<String, Object> conditions(@RequestBody final ConditionsQuery request) {
    final var result = conditionsService.conditions(request);
    return response(
        request,
        result.sql(),
        Map.of(
            "elements",
            result.elements(),
            "flows",
            result.flows(),
            "firstSeen",
            result.firstSeen()));
  }

  @SuppressWarnings("unchecked")
  private List<SeriesQuery> resolveScreenCandidates(final Object candidates) {
    if (!(candidates instanceof final List<?> list)) {
      return null; // "auto" (the literal string) or absent both mean auto.
    }
    return list.stream().map(item -> objectMapper.convertValue(item, SeriesQuery.class)).toList();
  }

  private List<String> resolveAttributes(final Object attributes) {
    if (!(attributes instanceof final List<?> list)) {
      return null; // "auto" or absent.
    }
    return list.stream().map(String::valueOf).toList();
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

  /** {@code POST /api/tools/screen} request. */
  public record ScreenRequest(SeriesQuery targetSeries, TimeRange window, Object candidates) {}

  /** {@code POST /api/tools/cohort-compare} request (raw {@code attributes} before resolution). */
  public record CohortCompareRequest(
      String entity,
      CohortSpec cohort,
      Map<String, Object> filters,
      String from,
      String to,
      Object attributes,
      Integer supportFloor) {}
}
