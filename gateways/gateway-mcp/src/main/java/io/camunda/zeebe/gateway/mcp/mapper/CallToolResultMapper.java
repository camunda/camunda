/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.mcp.mapper;

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

  public static CallToolResult fromPrimitive(final String content) {
    return CallToolResult.builder().addTextContent(content).build();
  }

  public static <T> CallToolResult fromPrimitive(
      final CompletableFuture<T> content, final Function<T, String> resultMapper) {
    return content
        .handleAsync(
            (resp, error) -> {
              if (error != null) {
                return mapErrorToResult(error);
              }
              return fromPrimitive(resultMapper.apply(resp));
            })
        .completeOnTimeout(
            mapProblemToResult(
                ProblemDetail.forStatusAndDetail(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "Didn't receive a response from the engine within 10 seconds.")),
            10L,
            TimeUnit.SECONDS)
        .join();
  }

  public static <T> CallToolResult fromPrimitive(
      final CompletableFuture<T> content,
      final Function<Throwable, CallToolResult> errorMapper,
      final Function<T, String> resultMapper) {
    return content
        .handleAsync(
            (resp, error) -> {
              if (error != null) {
                return errorMapper.apply(error);
              }
              return fromPrimitive(resultMapper.apply(resp));
            })
        .completeOnTimeout(
            mapProblemToResult(
                ProblemDetail.forStatusAndDetail(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "Didn't receive a response from the engine within 10 seconds.")),
            10L,
            TimeUnit.SECONDS)
        .join();
  }

  public static <T> CallToolResult from(
      final CompletableFuture<T> content, final Function<T, Object> resultMapper) {
    return content
        .handleAsync(
            (resp, error) -> {
              if (error != null) {
                return mapErrorToResult(error);
              }
              return from(resultMapper.apply(resp));
            })
        .completeOnTimeout(
            mapProblemToResult(
                ProblemDetail.forStatusAndDetail(
                    HttpStatus.GATEWAY_TIMEOUT, "Didn't receive a response within 10 seconds.")),
            10L,
            TimeUnit.SECONDS)
        .join();
  }

  public static CallToolResult mapErrorToResult(final Throwable error) {
    return mapProblemToResult(McpErrorMapper.mapErrorToProblem(error));
  }

  public static CallToolResult mapProblemToResult(final ProblemDetail problemDetail) {
    // TODO how widely supported is structured content in MCP clients?
    return CallToolResult.builder().structuredContent(problemDetail).isError(true).build();
  }

  public static CallToolResult mapViolationsToResult(final List<String> violations) {
    final ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST, "Validation errors: " + violations);
    return CallToolResult.builder().structuredContent(problemDetail).isError(true).build();
  }
}
