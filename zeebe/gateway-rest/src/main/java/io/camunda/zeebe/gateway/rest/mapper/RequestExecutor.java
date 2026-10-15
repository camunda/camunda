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
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.async.DeferredResult;

public class RequestExecutor {

  public static DeferredResult<ResponseEntity<Object>> toDeferredResult(
      final CompletableFuture<ResponseEntity<Object>> future, final Long timeout) {
    final var deferred = new DeferredResult<ResponseEntity<Object>>(timeout);
    future.whenComplete(
        (result, error) -> {
          if (error != null) {
            deferred.setErrorResult(
                error instanceof CompletionException && error.getCause() != null
                    ? error.getCause()
                    : error);
          } else {
            deferred.setResult(result);
          }
        });
    return deferred;
  }
}
