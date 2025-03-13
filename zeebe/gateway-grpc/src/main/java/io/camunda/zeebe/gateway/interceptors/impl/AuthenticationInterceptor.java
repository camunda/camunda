/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.interceptors.impl;

import io.camunda.zeebe.util.Either.Left;
import io.camunda.zeebe.util.Either.Right;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

public class AuthenticationInterceptor implements ServerInterceptor {
  private static final Metadata.Key<String> AUTH_KEY =
      Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);

  private final AuthenticationHandler authenticationHandler;

  public AuthenticationInterceptor(final AuthenticationHandler authenticationHandler) {
    this.authenticationHandler = authenticationHandler;
  }

  @Override
  public <ReqT, RespT> Listener<ReqT> interceptCall(
      final ServerCall<ReqT, RespT> call,
      final Metadata headers,
      final ServerCallHandler<ReqT, RespT> next) {

    final var authorization = headers.get(AUTH_KEY);
    if (authorization == null) {
      return deny(
          call,
          Status.UNAUTHENTICATED.augmentDescription(
              "Expected authentication information at header with key [%s], but found nothing"
                  .formatted(AUTH_KEY.name())));
    }

    return switch (authenticationHandler.authenticate(authorization)) {
      case Left<Status, Context>(final var status) -> deny(call, status);
      case Right<Status, Context>(final var context) ->
          Contexts.interceptCall(context, call, headers, next);
    };
  }

  private static <ReqT> ServerCall.Listener<ReqT> deny(
      final ServerCall<ReqT, ?> call, final Status status) {
    call.close(status, new Metadata());
    return new ServerCall.Listener<>() {};
  }
}
