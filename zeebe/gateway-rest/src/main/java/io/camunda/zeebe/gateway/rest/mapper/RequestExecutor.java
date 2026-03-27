/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.mapper;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

public class RequestExecutor {

  /**
   * Executes an async broker call synchronously via {@code .join()} and returns 204 No Content.
   *
   * <p>With Java 21 virtual threads, {@code .join()} unmounts the virtual thread during the broker
   * round-trip — zero platform threads are held while waiting. This is performance-equivalent to
   * returning the {@code CompletableFuture} directly.
   *
   * <p>If the broker call fails, the {@code CompletionException} is unwrapped and re-thrown so that
   * the global exception handler receives the original exception.
   */
  public static <T> ResponseEntity<Void> executeSync(final Supplier<CompletableFuture<T>> method) {
    try {
      method.get().join();
      return ResponseEntity.noContent().build();
    } catch (final CompletionException e) {
      throw e.getCause() instanceof RuntimeException re ? re : new RuntimeException(e.getCause());
    }
  }

  /**
   * Executes an async broker call synchronously via {@code .join()} and maps the result to a
   * response with the given status.
   */
  public static <BrokerResponseT, HttpResp> ResponseEntity<HttpResp> executeSync(
      final Supplier<CompletableFuture<BrokerResponseT>> method,
      final Function<BrokerResponseT, HttpResp> resultMapper,
      final HttpStatus responseStatus) {
    try {
      final BrokerResponseT response = method.get().join();
      return ResponseEntity.status(responseStatus).body(resultMapper.apply(response));
    } catch (final CompletionException e) {
      throw e.getCause() instanceof RuntimeException re ? re : new RuntimeException(e.getCause());
    }
  }

  public static <BrokerResponseT, HttpResp>
      CompletableFuture<ResponseEntity<Object>> executeServiceMethod(
          final Supplier<CompletableFuture<BrokerResponseT>> method,
          final Function<BrokerResponseT, HttpResp> resultMapper,
          final HttpStatus responseStatus) {
    return method
        .get()
        .handleAsync(
            (response, error) ->
                RestErrorMapper.getResponse(error)
                    .orElseGet(
                        () ->
                            ResponseEntity.status(responseStatus)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(resultMapper.apply(response))));
  }

  public static <BrokerResponseT, HttpResp>
      CompletableFuture<ResponseEntity<Object>> executeServiceMethod(
          final Supplier<CompletableFuture<BrokerResponseT>> method,
          final Function<BrokerResponseT, HttpResp> resultMapper,
          final Function<HttpResp, HttpStatus> responseStatusMapper) {
    return method
        .get()
        .handleAsync(
            (response, error) ->
                RestErrorMapper.getResponse(error)
                    .orElseGet(
                        () -> {
                          final var body = resultMapper.apply(response);
                          return ResponseEntity.status(responseStatusMapper.apply(body))
                              .contentType(MediaType.APPLICATION_JSON)
                              .body(body);
                        }));
  }

  public static <BrokerResponseT> CompletableFuture<ResponseEntity<Object>> executeServiceMethod(
      final Supplier<CompletableFuture<BrokerResponseT>> method,
      final Function<BrokerResponseT, ResponseEntity<Object>> result) {
    return method
        .get()
        .handleAsync(
            (response, error) ->
                RestErrorMapper.getResponse(error).orElseGet(() -> result.apply(response)));
  }

  public static <BrokerResponseT>
      CompletableFuture<ResponseEntity<Object>> executeServiceMethodWithNoContentResult(
          final Supplier<CompletableFuture<BrokerResponseT>> method) {
    return RequestExecutor.executeServiceMethod(method, ignored -> null, HttpStatus.NO_CONTENT);
  }
}
