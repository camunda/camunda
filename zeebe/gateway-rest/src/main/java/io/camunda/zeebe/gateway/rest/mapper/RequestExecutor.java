/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.mapper;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public class RequestExecutor {
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
                          return ResponseEntity.status(responseStatusMapper.apply(body)).body(body);
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
