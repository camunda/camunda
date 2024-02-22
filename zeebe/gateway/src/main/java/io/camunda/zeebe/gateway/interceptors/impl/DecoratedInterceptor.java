/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.interceptors.impl;

import io.camunda.zeebe.util.jar.ThreadContextUtil;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import org.agrona.LangUtil;

/**
 * Decorates third-party interceptors in order to ensure that the thread context class loader is
 * correctly set (required for interceptors loaded from external, isolated JARs).
 */
public final class DecoratedInterceptor implements ServerInterceptor {
  private final ServerInterceptor delegate;
  private final ClassLoader classLoader;

  DecoratedInterceptor(final ServerInterceptor delegate, final ClassLoader classLoader) {
    this.delegate = delegate;
    this.classLoader = classLoader;
  }

  public static DecoratedInterceptor decorate(final ServerInterceptor interceptor) {
    return new DecoratedInterceptor(interceptor, interceptor.getClass().getClassLoader());
  }

  @Override
  public <ReqT, RespT> Listener<ReqT> interceptCall(
      final ServerCall<ReqT, RespT> call,
      final Metadata headers,
      final ServerCallHandler<ReqT, RespT> next) {
    try {
      return ThreadContextUtil.callWithClassLoader(
          () -> delegate.interceptCall(call, headers, next), classLoader);
    } catch (final Exception e) {
      LangUtil.rethrowUnchecked(e);
      throw new UnsupportedOperationException(
          "Unexpectedly reached unreachable code; an exception should have been thrown beforehand");
    }
  }
}
