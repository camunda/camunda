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
import io.camunda.identity.sdk.tenants.dto.Tenant;
import io.camunda.zeebe.gateway.impl.configuration.IdentityCfg;
import io.camunda.zeebe.gateway.impl.configuration.MultiTenancyCfg;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class IdentityInterceptor implements ServerInterceptor {
  public static final Context.Key<List<String>> AUTHORIZED_TENANTS_KEY =
      Context.key("io.camunda.zeebe:authorized_tenants");

  private static final Logger LOGGER = LoggerFactory.getLogger(IdentityInterceptor.class);
  private static final Metadata.Key<String> AUTH_KEY =
      Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);
  private final Identity identity;
  private final MultiTenancyCfg multiTenancy;

  public IdentityInterceptor(final IdentityCfg config, final MultiTenancyCfg multiTenancy) {
    this(createIdentity(config), multiTenancy);
  }

  public IdentityInterceptor(final Identity identity, final MultiTenancyCfg multiTenancy) {
    this.identity = identity;
    this.multiTenancy = multiTenancy;
  }

  private static Identity createIdentity(final IdentityCfg config) {
    return new Identity(
        new IdentityConfiguration.Builder()
            .withIssuerBackendUrl(config.getIssuerBackendUrl())
            .withAudience(config.getAudience())
            .withType(config.getType().name())
            .withBaseUrl(config.getBaseUrl())
            .build());
  }

  @Override
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
      final ServerCall<ReqT, RespT> call,
      final Metadata headers,
      final ServerCallHandler<ReqT, RespT> next) {
    final var methodDescriptor = call.getMethodDescriptor();

    final var authorization = headers.get(AUTH_KEY);
    if (authorization == null) {
      LOGGER.debug(
          "Denying call {} as no token was provided", methodDescriptor.getFullMethodName());
      return deny(
          call,
          Status.UNAUTHENTICATED.augmentDescription(
              "Expected bearer token at header with key [%s], but found nothing"
                  .formatted(AUTH_KEY.name())));
    }

    final String token = authorization.replaceFirst("^Bearer ", "");
    try {
      identity.authentication().verifyToken(token);
    } catch (final TokenVerificationException e) {
      LOGGER.debug(
          "Denying call {} as the token could not be verified successfully. Error message: {}",
          methodDescriptor.getFullMethodName(),
          e.getMessage(),
          e);

      return deny(
          call,
          Status.UNAUTHENTICATED
              .augmentDescription("Failed to parse bearer token, see cause for details")
              .withCause(e));
    }

    if (!multiTenancy.isEnabled()) {
      return next.startCall(call, headers);
    }

    try {
      final List<String> authorizedTenants =
          identity.tenants().forToken(token).stream().map(Tenant::getTenantId).toList();
      final var context = Context.current().withValue(AUTHORIZED_TENANTS_KEY, authorizedTenants);
      return Contexts.interceptCall(context, call, headers, next);

    } catch (final RuntimeException e) {
      LOGGER.debug(
          "Denying call {} as the authorized tenants could not be retrieved from Identity. Error message: {}",
          methodDescriptor.getFullMethodName(),
          e.getMessage());
      return deny(
          call,
          Status.UNAUTHENTICATED
              .augmentDescription(
                  "Expected Identity to provide authorized tenants, see cause for details")
              .withCause(e));
    }
  }

  private <ReqT> ServerCall.Listener<ReqT> deny(
      final ServerCall<ReqT, ?> call, final Status status) {
    call.close(status, new Metadata());
    return new ServerCall.Listener<>() {};
  }
}
