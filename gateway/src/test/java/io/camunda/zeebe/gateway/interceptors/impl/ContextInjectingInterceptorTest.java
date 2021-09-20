/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.interceptors.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.zeebe.gateway.interceptors.InterceptorUtil;
import io.camunda.zeebe.gateway.query.QueryApi;
import io.grpc.Metadata;
import io.grpc.internal.NoopServerCall;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
final class ContextInjectingInterceptorTest {
  @Test
  void shouldInjectQueryApi() {
    // given
    final var api = mock(QueryApi.class);
    final var interceptor = new ContextInjectingInterceptor(api);
    final AtomicReference<QueryApi> injectedApi = new AtomicReference<>();

    // when
    interceptor.interceptCall(
        new NoopServerCall<>() {},
        new Metadata(),
        (call, headers) -> {
          injectedApi.set(InterceptorUtil.getQueryApiKey().get());
          return null;
        });

    // then
    assertThat(injectedApi).hasValue(api);
  }
}
