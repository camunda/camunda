/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.interceptors.impl;

import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.IdentityConfiguration;
import io.camunda.identity.sdk.authentication.exception.TokenVerificationException;
import io.camunda.zeebe.gateway.impl.configuration.IdentityCfg;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class IdentityInterceptor implements ServerInterceptor {
  private static final Logger LOGGER = LoggerFactory.getLogger(IdentityInterceptor.class);
  private static final Metadata.Key<String> AUTH_KEY =
      Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);

  private final Identity identity;

  public IdentityInterceptor(final IdentityCfg config) {
    this(createIdentity(config));
  }

  IdentityInterceptor(final Identity identity) {
    this.identity = identity;
  }

  private static Identity createIdentity(final IdentityCfg config) {
    return new Identity(
        new IdentityConfiguration.Builder()
            .withIssuerBackendUrl(config.getIssuerBackendUrl())
            .withAudience(config.getAudience())
            .withType(config.getType().name())
            .build());
  }

  @Override
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
      final ServerCall<ReqT, RespT> call,
      final Metadata headers,
      final ServerCallHandler<ReqT, RespT> next) {
    final var methodDescriptor = call.getMethodDescriptor();

    final var token = headers.get(AUTH_KEY);
    if (token == null) {
      LOGGER.debug(
          "Denying call {} as no token was provided", methodDescriptor.getFullMethodName());
      return deny(
          call,
          Status.UNAUTHENTICATED.augmentDescription(
              "Expected bearer token at header with key [%s], but found nothing"
                  .formatted(AUTH_KEY.name())));
    }

    try {
      identity.authentication().verifyToken(token.replaceFirst("^Bearer ", ""));
    } catch (final TokenVerificationException e) {
      LOGGER.debug(
          "Denying call {} as the token could not be fully verified. Error message: {}",
          methodDescriptor.getFullMethodName(),
          e.getMessage());
      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace(
            "Token used for call {} was [{}]", methodDescriptor.getFullMethodName(), token, e);
      }

      return deny(
          call,
          Status.UNAUTHENTICATED
              .augmentDescription("Failed to parse bearer token, see cause for details")
              .withCause(e));
    }

    return next.startCall(call, headers);
  }

  private <ReqT> ServerCall.Listener<ReqT> deny(
      final ServerCall<ReqT, ?> call, final Status status) {
    call.close(status, new Metadata());
    return new ServerCall.Listener<>() {};
  }
}
