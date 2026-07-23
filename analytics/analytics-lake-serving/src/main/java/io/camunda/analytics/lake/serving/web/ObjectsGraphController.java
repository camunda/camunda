/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.web;

import io.camunda.analytics.lake.serving.objects.ObjectsGraphService;
import io.camunda.analytics.lake.serving.objects.ObjectsGraphService.GraphQuery;
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

/**
 * The object-web graph endpoints ({@code GET /api/objects/type-map}, {@code POST
 * /api/objects/graph}) — see {@link ObjectsGraphService}. Kept as its own controller (rather than
 * folded into {@link ObjectsController}) since it's a separately owned slice of the same {@code
 * /api/objects} path.
 */
@RestController
@RequestMapping("/api/objects")
public class ObjectsGraphController {

  private final ObjectsGraphService objectsGraphService;

  public ObjectsGraphController(final ObjectsGraphService objectsGraphService) {
    this.objectsGraphService = objectsGraphService;
  }

  @GetMapping("/type-map")
  public Map<String, Object> typeMap() {
    final var result = objectsGraphService.typeMap();
    return response(result.sql(), Map.of("edges", result.edges()));
  }

  @PostMapping("/graph")
  public Map<String, Object> graph(@RequestBody final GraphQuery request) {
    final var result = objectsGraphService.graph(request);
    return response(
        result.sql(),
        Map.of("nodes", result.nodes(), "edges", result.edges(), "truncated", result.truncated()),
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
