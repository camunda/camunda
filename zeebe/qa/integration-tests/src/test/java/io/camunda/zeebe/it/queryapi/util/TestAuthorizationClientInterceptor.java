/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.queryapi.util;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.MethodDescriptor;

public final class TestAuthorizationClientInterceptor implements ClientInterceptor {
  private final String tenant;

  public TestAuthorizationClientInterceptor(final String tenant) {
    this.tenant = tenant;
  }

  @Override
  public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
      final MethodDescriptor<ReqT, RespT> method,
      final CallOptions callOptions,
      final Channel next) {
    final var credentials = new TestAuthorizationCallCredentials(tenant);
    return next.newCall(method, callOptions.withCallCredentials(credentials));
  }
}
