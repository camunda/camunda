/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.mcp.mapper;

import io.camunda.service.exception.ErrorMapper;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.zeebe.util.Either;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

public class CallToolResultMapper {
  public static CallToolResult from(final Object content) {
    return CallToolResult.builder().structuredContent(content).build();
  }

  public static <T> CallToolResult from(
      final CompletableFuture<T> content, final Function<T, Object> resultMapper) {
    return from(content, resultMapper, CallToolResultMapper::mapErrorToResult);
  }

  public static <T> CallToolResult from(
      final CompletableFuture<T> content,
      final Function<T, Object> resultMapper,
      final Function<Throwable, CallToolResult> errorMapper) {
    return fromInternal(content, resp -> from(resultMapper.apply(resp)), errorMapper);
  }

  public static CallToolResult fromPrimitive(final String content) {
    return CallToolResult.builder().addTextContent(content).build();
  }

  public static <T> CallToolResult fromPrimitive(
      final CompletableFuture<T> content, final Function<T, String> resultMapper) {
    return fromPrimitive(content, resultMapper, CallToolResultMapper::mapErrorToResult);
  }

  public static <T> CallToolResult fromPrimitive(
      final CompletableFuture<T> content,
      final Function<T, String> resultMapper,
      final Function<Throwable, CallToolResult> errorMapper) {
    return fromInternal(content, resp -> fromPrimitive(resultMapper.apply(resp)), errorMapper);
  }

  private static <T> CallToolResult fromInternal(
      final CompletableFuture<T> content,
      final Function<T, CallToolResult> resultMapper,
      final Function<Throwable, CallToolResult> errorMapper) {
    return content
        .handleAsync(
            (resp, error) -> {
              if (error != null) {
                return errorMapper.apply(error);
              }
              return resultMapper.apply(resp);
            })
        .completeOnTimeout(
            mapProblemToResult(
                ProblemDetail.forStatusAndDetail(
                    HttpStatus.GATEWAY_TIMEOUT, "Didn't receive a response within 10 seconds.")),
            10L,
            TimeUnit.SECONDS)
        .join();
  }

  public static <T> Either<ServiceException, T> executeServiceMethod(
      final CompletableFuture<T> content) {
    return content
        .<Either<ServiceException, T>>handleAsync(
            (resp, error) -> {
              if (error != null) {
                return Either.left(ErrorMapper.mapError(error));
              }
              return Either.right(resp);
            })
        .completeOnTimeout(
            Either.left(
                new ServiceException(
                    "Didn't receive a response within 10 seconds.", Status.DEADLINE_EXCEEDED)),
            10L,
            TimeUnit.SECONDS)
        .join();
  }

  public static CallToolResult mapErrorToResult(final Throwable error) {
    return mapProblemToResult(McpErrorMapper.mapErrorToProblem(error));
  }

  private static CallToolResult mapProblemToResult(final ProblemDetail problemDetail) {
    return CallToolResult.builder().structuredContent(problemDetail).isError(true).build();
  }

  public static CallToolResult mapViolationsToResult(final List<String> violations) {
    final ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST, "Validation errors: " + violations);
    return CallToolResult.builder().structuredContent(problemDetail).isError(true).build();
  }
}
