/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.mcp.mapper;

import static io.camunda.zeebe.protocol.record.RejectionType.INVALID_ARGUMENT;

import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

public class McpErrorMapper {
  public static final Function<String, Exception> RESOURCE_EXHAUSTED_EXCEPTION_PROVIDER =
      msg -> new ServiceException(msg, ServiceException.Status.RESOURCE_EXHAUSTED);
  public static final Function<String, Throwable> REQUEST_CANCELED_EXCEPTION_PROVIDER =
      RuntimeException::new;

  public static final Logger LOG = LoggerFactory.getLogger(McpErrorMapper.class);

  public static <T> Optional<ResponseEntity<T>> getResponse(final Throwable error) {
    return Optional.ofNullable(error)
        .map(McpErrorMapper::mapErrorToProblem)
        .map(McpErrorMapper::mapProblemToResponse);
  }

  public static ProblemDetail mapErrorToProblem(final Throwable error) {
    if (error == null) {
      return null;
    }
    // SeviceExceptions can be wrapped in Java exceptions because they are handled in Java futures

    if (unwrapError(error) instanceof final ServiceException se) {
      return createProblemDetail(mapStatus(se.getStatus()), se.getMessage(), se.getStatus().name());
    } else {
      LOG.error("Expected to handle REST request, but an unexpected error occurred", error);
      return createProblemDetail(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "Unexpected error occurred during the request processing: " + error.getMessage(),
          error.getClass().getName());
    }
  }

  public static Throwable unwrapError(final Throwable throwable) {
    if (throwable instanceof CompletionException || throwable instanceof ExecutionException) {
      return throwable.getCause();
    }
    return throwable;
  }

  public static <T> ResponseEntity<T> mapErrorToResponse(@NotNull final Throwable error) {
    return mapProblemToResponse(mapErrorToProblem(error));
  }

  public static ProblemDetail createProblemDetail(final List<String> violations) {
    String problems = String.join(". ", violations);
    if (!problems.endsWith(".")) {
      problems = problems + ".";
    }

    return createProblemDetail(HttpStatus.BAD_REQUEST, problems, INVALID_ARGUMENT.name());
  }

  public static ProblemDetail createProblemDetail(
      final HttpStatusCode status, final String detail, final String title) {
    final var problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
    problemDetail.setTitle(title);
    return problemDetail;
  }

  public static <T> ResponseEntity<T> mapProblemToResponse(final ProblemDetail problemDetail) {
    return ResponseEntity.of(problemDetail)
        .headers(httpHeaders -> httpHeaders.setContentType(MediaType.APPLICATION_PROBLEM_JSON))
        .build();
  }

  public static <T> CompletableFuture<ResponseEntity<T>> mapProblemToCompletedResponse(
      final ProblemDetail problemDetail) {
    return CompletableFuture.completedFuture(McpErrorMapper.mapProblemToResponse(problemDetail));
  }

  public static HttpStatus mapStatus(final Status status) {
    return switch (status) {
      case ABORTED -> HttpStatus.BAD_GATEWAY;
      case UNAVAILABLE, RESOURCE_EXHAUSTED -> HttpStatus.SERVICE_UNAVAILABLE;
      case UNKNOWN, INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR;
      case FORBIDDEN -> HttpStatus.FORBIDDEN;
      case NOT_FOUND -> HttpStatus.NOT_FOUND;
      case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
      case ALREADY_EXISTS, INVALID_STATE -> HttpStatus.CONFLICT;
      case INVALID_ARGUMENT -> HttpStatus.BAD_REQUEST;
      case DEADLINE_EXCEEDED -> HttpStatus.GATEWAY_TIMEOUT;
    };
  }
}
