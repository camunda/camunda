/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.mapper;

import io.camunda.gateway.mapping.http.GatewayErrorMapper;
import io.camunda.zeebe.util.Either;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.springframework.http.ProblemDetail;

public class CallToolResultMapper {

  public static CallToolResult from(final Object content) {
    return CallToolResult.builder().structuredContent(content).build();
  }

  public static <T> CallToolResult from(
      final CompletableFuture<T> content, final Function<T, Object> resultMapper) {
    return fromInternal(
        content, resp -> from(resultMapper.apply(resp)), CallToolResultMapper::mapErrorToResult);
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

  private static CallToolResult fromPrimitive(final String content) {
    return CallToolResult.builder().addTextContent(content).build();
  }

  private static <T> CallToolResult fromInternal(
      final CompletableFuture<T> content,
      final Function<T, CallToolResult> resultMapper,
      final Function<Throwable, CallToolResult> errorMapper) {
    return executeServiceMethod(content).fold(errorMapper, resultMapper);
  }

  public static <T> Either<Throwable, T> executeServiceMethod(final CompletableFuture<T> content) {
    return content
        .<Either<Throwable, T>>handleAsync(
            (resp, error) -> {
              if (error != null) {
                return Either.left(error);
              }
              return Either.right(resp);
            })
        .join();
  }

  public static CallToolResult mapProblemToResult(final ProblemDetail problemDetail) {
    return CallToolResult.builder().structuredContent(problemDetail).isError(true).build();
  }

  public static CallToolResult mapErrorToResult(final Throwable error) {
    return mapProblemToResult(GatewayErrorMapper.mapErrorToProblem(error));
  }
}
