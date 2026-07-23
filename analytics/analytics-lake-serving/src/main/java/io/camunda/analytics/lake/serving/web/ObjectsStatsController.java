/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.web;

import io.camunda.analytics.lake.serving.objects.ObjectsStatsService;
import io.camunda.analytics.lake.serving.objects.ObjectsStatsService.ObjectsStatsQuery;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** {@code POST /api/objects/stats} -- see {@link ObjectsStatsService}. */
@RestController
@RequestMapping("/api/objects")
public class ObjectsStatsController {

  private final ObjectsStatsService objectsStatsService;

  public ObjectsStatsController(final ObjectsStatsService objectsStatsService) {
    this.objectsStatsService = objectsStatsService;
  }

  @PostMapping("/stats")
  public Map<String, Object> stats(@RequestBody final ObjectsStatsQuery request) {
    final var result = objectsStatsService.stats(request);
    final Map<String, Object> response = new LinkedHashMap<>();
    response.put("byProcess", result.byProcess());
    response.put("relationFanout", result.relationFanout());
    response.put("outcomes", result.outcomes());
    response.put("sql", result.sql());
    response.put("params", request);
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
