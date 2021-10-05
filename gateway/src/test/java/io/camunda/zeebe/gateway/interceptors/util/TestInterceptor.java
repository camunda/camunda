/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.interceptors.util;

import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.DeployProcessRequest;
import io.camunda.zeebe.test.util.grpc.CloseAwareListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

/**
 * A dummy interceptor which aborts deployments with a specific error message. The class must be
 * public since we will load it via JAR into the gateway.
 */
public final class TestInterceptor implements ServerInterceptor {
  static final String ERROR_MESSAGE = "Aborting because of test";

  @Override
  public <ReqT, RespT> Listener<ReqT> interceptCall(
      final ServerCall<ReqT, RespT> call,
      final Metadata headers,
      final ServerCallHandler<ReqT, RespT> next) {
    final var listener = next.startCall(call, headers);
    return new CloseAwareListener<>(listener) {
      @Override
      public void onMessage(final ReqT message) {
        if (message instanceof DeployProcessRequest) {
          call.close(Status.PERMISSION_DENIED.augmentDescription(ERROR_MESSAGE), new Metadata());
          isClosed = true;
          return;
        }

        super.onMessage(message);
      }
    };
  }
}
