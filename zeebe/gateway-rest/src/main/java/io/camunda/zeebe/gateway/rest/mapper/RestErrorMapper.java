/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.mapper;

import io.camunda.gateway.model.mapper.GatewayErrorMapper;
import jakarta.validation.constraints.NotNull;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

public class RestErrorMapper {
  public static final Logger LOG = LoggerFactory.getLogger(RestErrorMapper.class);

  public static <T> Optional<ResponseEntity<T>> getResponse(final Throwable error) {
    return Optional.ofNullable(error)
        .map(GatewayErrorMapper::mapErrorToProblem)
        .map(RestErrorMapper::mapProblemToResponse);
  }

  public static <T> ResponseEntity<T> mapErrorToResponse(@NotNull final Throwable error) {
    return mapProblemToResponse(GatewayErrorMapper.mapErrorToProblem(error));
  }

  public static <T> ResponseEntity<T> mapProblemToResponse(final ProblemDetail problemDetail) {
    return ResponseEntity.of(problemDetail)
        .headers(httpHeaders -> httpHeaders.setContentType(MediaType.APPLICATION_PROBLEM_JSON))
        .build();
  }

  public static <T> CompletableFuture<ResponseEntity<T>> mapProblemToCompletedResponse(
      final ProblemDetail problemDetail) {
    return CompletableFuture.completedFuture(RestErrorMapper.mapProblemToResponse(problemDetail));
  }
}
