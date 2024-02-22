/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.interceptors.impl;

import io.camunda.zeebe.gateway.interceptors.InterceptorUtil;
import io.camunda.zeebe.gateway.query.QueryApi;
import io.grpc.Context;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import org.agrona.LangUtil;

/**
 * An interceptor implementation which injects common context for further interceptors. It's
 * expected to be one of the top level interceptors. This is for context objects which are the same
 * and shared by all interceptors, as opposed to the {@link DecoratedInterceptor} which injects
 * context information specific to the wrapped interceptor.
 *
 * <p>This interceptor will inject the following in every call's context:
 *
 * <ul>
 *   <li>{@link InterceptorUtil#getQueryApi()} => returns a query API usable by the interceptors
 * </ul>
 */
public final class ContextInjectingInterceptor implements ServerInterceptor {
  private final QueryApi queryApi;

  public ContextInjectingInterceptor(final QueryApi queryApi) {
    this.queryApi = queryApi;
  }

  @Override
  public <ReqT, RespT> Listener<ReqT> interceptCall(
      final ServerCall<ReqT, RespT> call,
      final Metadata headers,
      final ServerCallHandler<ReqT, RespT> next) {
    try {
      return Context.current()
          .withValue(InterceptorUtil.getQueryApiKey(), queryApi)
          .call(() -> next.startCall(call, headers));
    } catch (final Exception e) {
      LangUtil.rethrowUnchecked(e);
      return null; // unreachable as the exception is rethrown above
    }
  }
}
