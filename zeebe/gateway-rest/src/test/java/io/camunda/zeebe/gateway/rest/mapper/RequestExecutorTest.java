/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

public class RequestExecutorTest {

  @Test
  void shouldUnwrapCompletionExceptionCause() {
    // given
    final var future = new CompletableFuture<ResponseEntity<Object>>();
    final var deferred = RequestExecutor.toDeferredResult(future, -1L);
    final var cause = new IllegalStateException("broker rejected the request");

    // when
    future.completeExceptionally(new CompletionException(cause));

    // then
    assertThat(deferred.getResult()).isSameAs(cause);
  }

  @Test
  void shouldPassThroughBareException() {
    // given
    final var future = new CompletableFuture<ResponseEntity<Object>>();
    final var deferred = RequestExecutor.toDeferredResult(future, -1L);
    final var error = new IllegalArgumentException("invalid request");

    // when
    future.completeExceptionally(error);

    // then
    assertThat(deferred.getResult()).isSameAs(error);
  }

  @Test
  void shouldPassThroughNormalResult() {
    // given
    final var future = new CompletableFuture<ResponseEntity<Object>>();
    final var deferred = RequestExecutor.toDeferredResult(future, -1L);
    final var response = ResponseEntity.ok().build();

    // when
    future.complete(response);

    // then
    assertThat(deferred.getResult()).isSameAs(response);
  }
}
