/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.queryapi.util;

import io.camunda.zeebe.gateway.interceptors.InterceptorUtil;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc;
import io.grpc.Metadata;
import io.grpc.Metadata.Key;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

public final class TestAuthorizationServerInterceptor implements ServerInterceptor {
  public static final Key<String> TENANT_KEY =
      Metadata.Key.of("zeebe-tenant", Metadata.ASCII_STRING_MARSHALLER);

  @SuppressWarnings("ConstantConditions")
  @Override
  public <ReqT, RespT> Listener<ReqT> interceptCall(
      final ServerCall<ReqT, RespT> call,
      final Metadata headers,
      final ServerCallHandler<ReqT, RespT> next) {
    final var api = InterceptorUtil.getQueryApiKey().get();
    final var tenant = headers.get(TENANT_KEY);

    // let Topology requests pass untouched
    if (GatewayGrpc.getTopologyMethod()
        .getSchemaDescriptor()
        .equals(call.getMethodDescriptor().getSchemaDescriptor())) {
      return next.startCall(call, headers);
    }

    if (tenant == null) {
      call.close(
          Status.UNAUTHENTICATED.augmentDescription(
              "No tenant specified; please add the tenant in the headers using the key zeebe-tenant"),
          headers);
      return new NoopListener<>();
    }

    final var listener = next.startCall(call, headers);
    return new TestAuthorizationListener<>(listener, api, call, tenant);
  }

  public static final class NoopListener<ReqT> extends ServerCall.Listener<ReqT> {}
}
