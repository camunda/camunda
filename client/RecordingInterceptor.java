/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.client;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import java.util.function.BiConsumer;

/**
 * Records the headers of the last intercepted call. Additionally, also allows the specification of
 * an action to be taken (e.g., modify headers, fail call, etc).
 */
public final class RecordingInterceptor implements ServerInterceptor {
  private Metadata capturedHeaders;
  private BiConsumer<ServerCall, Metadata> interceptAction;

  @Override
  public <ReqT, RespT> Listener<ReqT> interceptCall(
      final ServerCall<ReqT, RespT> call,
      final Metadata headers,
      final ServerCallHandler<ReqT, RespT> next) {
    capturedHeaders = headers;

    if (interceptAction != null) {
      interceptAction.accept(call, headers);
    }
    return next.startCall(call, headers);
  }

  /**
   * Get the headers captured by the last call or null if none exist.
   *
   * @return headers
   */
  Metadata getCapturedHeaders() {
    return capturedHeaders;
  }

  /** Resets the captured headers and the action to be taken when a call is intercepted. */
  void reset() {
    capturedHeaders = null;
    interceptAction = null;
  }

  /**
   * Sets an action that will be taken when a call is intercepted. Can be used to modify headers,
   * reject calls, etc.
   *
   * @param interceptAction
   */
  void setInterceptAction(final BiConsumer<ServerCall, Metadata> interceptAction) {
    this.interceptAction = interceptAction;
  }
}
