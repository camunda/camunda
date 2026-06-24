/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.interceptors.impl;

import static io.camunda.configuration.api.physicaltenants.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;

import io.camunda.zeebe.gateway.interceptors.InterceptorUtil;
import io.camunda.zeebe.gateway.protocol.GrpcHeaders;
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
import java.util.Map;
import java.util.Set;

/**
 * Merged physical-tenant + authentication interceptor.
 *
 * <p>Reads the {@code Camunda-Physical-Tenant} header once, stamps the resolved id into the gRPC
 * {@link Context} (consumed downstream by {@code EndpointManager} for partition-group routing),
 * then selects the per-PT {@link AuthenticationHandler} from the registry and authenticates — but
 * ONLY when the API is not unprotected. The stamp happens for every call, including unprotected
 * ones.
 *
 * <p>Fail-fast at registry build time (see {@link PhysicalTenantHandlerFactory}) ensures every
 * known PT id is represented; unknown ids are rejected at request time with {@link
 * Status#NOT_FOUND}.
 */
public final class AuthenticationInterceptor implements ServerInterceptor {

  private static final Metadata.Key<String> AUTH_KEY =
      Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);

  private final Set<String> knownTenantIds;
  private final Map<String, AuthenticationHandler> handlersByTenantId;
  private final boolean authEnabled;
  private final AuthenticationMetrics metrics;

  public AuthenticationInterceptor(
      final Set<String> knownTenantIds,
      final Map<String, AuthenticationHandler> handlersByTenantId,
      final boolean authEnabled,
      final AuthenticationMetrics metrics) {
    this.knownTenantIds = Set.copyOf(knownTenantIds);
    this.handlersByTenantId = Map.copyOf(handlersByTenantId);
    this.authEnabled = authEnabled;
    this.metrics = metrics;
  }

  @Override
  public <ReqT, RespT> Listener<ReqT> interceptCall(
      final ServerCall<ReqT, RespT> call,
      final Metadata headers,
      final ServerCallHandler<ReqT, RespT> next) {

    final var tenantHeaderValue = headers.get(GrpcHeaders.PHYSICAL_TENANT);
    final var tenantId = tenantHeaderValue != null ? tenantHeaderValue : DEFAULT_PHYSICAL_TENANT_ID;

    if (!knownTenantIds.contains(tenantId)) {
      call.close(
          Status.NOT_FOUND.withDescription("Unknown physical tenant: " + tenantId), new Metadata());
      return new ServerCall.Listener<>() {};
    }

    final Context contextWithTenant =
        Context.current().withValue(InterceptorUtil.getPhysicalTenantIdKey(), tenantId);

    if (!authEnabled) {
      return Contexts.interceptCall(contextWithTenant, call, headers, next);
    }

    final var authorization = headers.get(AUTH_KEY);
    if (authorization == null) {
      call.close(
          Status.UNAUTHENTICATED.augmentDescription(
              "Expected authentication information at header with key [%s], but found nothing"
                  .formatted(AUTH_KEY.name())),
          new Metadata());
      return new ServerCall.Listener<>() {};
    }

    final var latencyTimer = metrics.startLatencySample();
    final var handler = handlersByTenantId.get(tenantId);
    return switch (handler.authenticate(authorization)) {
      case Left<Status, Context>(final var status) -> {
        metrics.recordFailureLatency(latencyTimer);
        call.close(status, new Metadata());
        yield new ServerCall.Listener<>() {};
      }
      case Right<Status, Context>(final var authContext) -> {
        metrics.recordSuccessLatency(latencyTimer);
        final var merged =
            authContext.withValue(InterceptorUtil.getPhysicalTenantIdKey(), tenantId);
        yield Contexts.interceptCall(merged, call, headers, next);
      }
    };
  }
}
