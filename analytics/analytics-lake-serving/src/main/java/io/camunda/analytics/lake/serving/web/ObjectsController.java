/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.web;

import io.camunda.analytics.lake.serving.objects.ObjectsService;
import io.camunda.analytics.lake.serving.objects.ObjectsService.JourneyQuery;
import io.camunda.analytics.lake.serving.objects.ObjectsService.ObjectListQuery;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The object-fabric read endpoints ({@code /api/objects/*}) — see {@link ObjectsService}. */
@RestController
@RequestMapping("/api/objects")
public class ObjectsController {

  private final ObjectsService objectsService;

  public ObjectsController(final ObjectsService objectsService) {
    this.objectsService = objectsService;
  }

  @GetMapping("/types")
  public Map<String, Object> types() {
    final var result = objectsService.types();
    return response(
        result.sql(), Map.of("types", result.types(), "closedSupported", result.closedSupported()));
  }

  @PostMapping("/list")
  public Map<String, Object> list(@RequestBody final ObjectListQuery request) {
    final var result = objectsService.list(request);
    return response(result.sql(), Map.of("rows", result.rows()), request);
  }

  @PostMapping("/journey")
  public Map<String, Object> journey(@RequestBody final JourneyQuery request) {
    final var result = objectsService.journey(request);
    return response(
        result.sql(),
        Map.of(
            "sightings",
            result.sightings(),
            "activities",
            result.activities(),
            "links",
            result.links(),
            "relations",
            result.relations()),
        request);
  }

  private Map<String, Object> response(final List<String> sql, final Map<String, Object> body) {
    final Map<String, Object> response = new LinkedHashMap<>(body);
    response.put("sql", sql);
    return response;
  }

  private Map<String, Object> response(
      final List<String> sql, final Map<String, Object> body, final Object params) {
    final Map<String, Object> response = response(sql, body);
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
