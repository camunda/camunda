/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl.interceptor;

import io.camunda.zeebe.gateway.Interceptor;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;

public class GrpcInterceptor implements ServerInterceptor {

  private final Interceptor delegate;

  public GrpcInterceptor(final Interceptor delegate) {
    this.delegate = delegate;
  }

  @Override
  public <ReqT, RespT> Listener<ReqT> interceptCall(
      final ServerCall<ReqT, RespT> call,
      final Metadata headers,
      final ServerCallHandler<ReqT, RespT> next) {

    final var request = new GrpcRequest(headers);
    final var control = new GrpcControl();
    delegate.intercept(request, control);

    if (control.isAccepted()) {
      final Context ctx = Context.current();
      return Contexts.interceptCall(ctx, call, headers, next);
    }

    call.close(control.getStatus(), new Metadata());
    return new ServerCall.Listener<>() {
      // noop
    };
  }
}
