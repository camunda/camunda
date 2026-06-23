/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.gateway.controller;

import io.camunda.eventbridge.gateway.dto.EventBridgeDtos;
import io.camunda.eventbridge.service.PollService;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consumer poll endpoint: {@code GET /v1/events/{partitionId}/poll}. Routes to the partition leader
 * and returns events inline as JSON (copy-based path, distinct from the zero-copy {@code /fetch}).
 */
@RestController
public class PollController {

  private final PollService pollService;

  public PollController(final PollService pollService) {
    this.pollService = pollService;
  }

  @GetMapping("/v1/events/{partitionId}/poll")
  public CompletableFuture<ResponseEntity<EventBridgeDtos.PollResponse>> poll(
      @PathVariable final int partitionId,
      @RequestParam(defaultValue = "-1") final long fromPosition,
      @RequestParam(defaultValue = "1024") final int maxRecords) {

    return pollService
        .poll(partitionId, fromPosition, maxRecords)
        .handle(
            (res, error) -> {
              if (error != null) {
                return ResponseEntity.internalServerError()
                    .body(EventBridgeDtos.PollResponse.error("POLL_FAILED", rootMessage(error)));
              }
              final List<EventBridgeDtos.PollEvent> events =
                  res.getEvents().stream()
                      .map(
                          e ->
                              new EventBridgeDtos.PollEvent(
                                  e.position(), Base64.getEncoder().encodeToString(e.payload())))
                      .toList();
              return ResponseEntity.ok(
                  EventBridgeDtos.PollResponse.ok(events, res.getNextPosition()));
            });
  }

  private static String rootMessage(final Throwable error) {
    final var cause = error.getCause() != null ? error.getCause() : error;
    return cause.getMessage() != null ? cause.getMessage() : cause.toString();
  }
}
