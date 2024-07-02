/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.interceptors.util;

import io.camunda.zeebe.gateway.interceptors.InterceptorUtil;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class TestTenantProvidingInterceptor implements ServerInterceptor {

  public static final AtomicInteger TENANT_CALLS = new AtomicInteger(0);

  @Override
  public <ReqT, RespT> Listener<ReqT> interceptCall(
      final ServerCall<ReqT, RespT> call,
      final Metadata headers,
      final ServerCallHandler<ReqT, RespT> next) {
    try {
      final var context = InterceptorUtil.setAuthorizedTenants(getAuthorizedTenants());
      return Contexts.interceptCall(context, call, headers, next);
    } catch (final Exception e) {
      // re-throw unexpected exception
      throw new RuntimeException(e);
    }
  }

  public static List<String> getAuthorizedTenants() {
    return List.of("tenant-" + TENANT_CALLS.incrementAndGet());
  }

  public static void resetInterceptorsCalls() {
    TENANT_CALLS.set(0);
  }
}
