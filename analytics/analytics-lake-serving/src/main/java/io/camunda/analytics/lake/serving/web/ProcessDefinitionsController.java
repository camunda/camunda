/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.web;

import io.camunda.analytics.lake.serving.objects.ProcessDefinitionsService;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/definitions?processId=...&version=...} and {@code GET /api/definitions/list} —
 * see {@link ProcessDefinitionsService}. For {@code /api/definitions}, a missing {@code
 * process_definitions} view or no matching row both come back as a clean 404 (the frontend's
 * Diagram tab hides itself on any error response, never surfacing a raw 500). {@code
 * /api/definitions/list} never 404s -- a missing view surfaces as an empty {@code definitions}
 * list, since its caller (the Processes page's picker) renders an empty state.
 */
@RestController
public class ProcessDefinitionsController {

  private final ProcessDefinitionsService processDefinitionsService;

  public ProcessDefinitionsController(final ProcessDefinitionsService processDefinitionsService) {
    this.processDefinitionsService = processDefinitionsService;
  }

  @GetMapping("/api/definitions")
  public Map<String, Object> get(
      @RequestParam final String processId, @RequestParam final int version) {
    final var result = processDefinitionsService.find(processId, version);
    return Map.of(
        "processId", result.processId(), "version", result.version(), "bpmnXml", result.bpmnXml());
  }

  @GetMapping("/api/definitions/list")
  public Map<String, Object> list() {
    final var result = processDefinitionsService.list();
    return Map.of("definitions", result.definitions(), "sql", result.sql());
  }

  @ExceptionHandler(NoSuchElementException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public Map<String, String> onNotFound(final NoSuchElementException e) {
    return Map.of("error", e.getMessage() == null ? "not found" : e.getMessage());
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
