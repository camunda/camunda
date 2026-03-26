/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.gateway.controller;

import io.camunda.eventbridge.mapper.ResponseMapper;
import io.camunda.eventbridge.service.PublishService;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Handles event publish requests: {@code POST /v1/events/{partitionId}}. */
@RestController
@RequestMapping("/v1/events")
public class PublishController {

  private final PublishService publishService;
  private final ResponseMapper responseMapper;

  public PublishController(
      final PublishService publishService, final ResponseMapper responseMapper) {
    this.publishService = publishService;
    this.responseMapper = responseMapper;
  }

  @PostMapping(
      value = "/{partitionId}",
      consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public CompletableFuture<ResponseEntity<Object>> publish(
      @PathVariable final int partitionId, @RequestBody final byte[] body) {

    return publishService
        .publish(body)
        .handleAsync(
            (res, error) -> {
              if (error != null) {
                return ResponseEntity.ok(null);
              }
              final var response = responseMapper.toPublishBatchResponse(res);
              return ResponseEntity.ok(response);
            });
  }
}
