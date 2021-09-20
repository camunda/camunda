/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.interceptors.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.internal.NoopServerCall;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
final class DecoratedInterceptorTest {

  @Test
  void shouldSetThreadContextClassLoader() {
    // given
    final var interceptor = new TestInterceptor();
    final var classLoader = new ClassLoader("testLoader", getClass().getClassLoader()) {};
    final var decorated = new DecoratedInterceptor(interceptor, classLoader);

    // when
    decorated.interceptCall(new NoopServerCall<>() {}, new Metadata(), (call, headers) -> null);

    // then
    assertThat(interceptor.threadContextClassLoader).isSameAs(classLoader);
  }

  private static final class TestInterceptor implements ServerInterceptor {
    private ClassLoader threadContextClassLoader;

    @Override
    public <ReqT, RespT> Listener<ReqT> interceptCall(
        final ServerCall<ReqT, RespT> call,
        final Metadata headers,
        final ServerCallHandler<ReqT, RespT> next) {
      threadContextClassLoader = Thread.currentThread().getContextClassLoader();
      return next.startCall(call, headers);
    }
  }
}
