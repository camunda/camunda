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
 * Reads the {@code Camunda-Physical-Tenant} header once, stamps the resolved id into the gRPC
 * {@link Context} (consumed downstream by {@code EndpointManager} for partition-group routing),
 * then selects the per-PT {@link AuthenticationHandler} from the registry and authenticates — but
 * ONLY when the API is protected. The stamp happens for every call, including unprotected ones.
 *
 * <p>An unknown physical tenant yields {@link Status#NOT_FOUND} on an unprotected API, and {@link
 * Status#UNAUTHENTICATED} on a protected one — so an unauthenticated caller cannot probe whether a
 * tenant exists.
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
    final var latencyTimer = metrics.startLatencySample();

    final var tenantHeaderValue = headers.get(GrpcHeaders.PHYSICAL_TENANT);
    final var tenantId = tenantHeaderValue != null ? tenantHeaderValue : DEFAULT_PHYSICAL_TENANT_ID;

    if (!authEnabled) {
      // unprotected API: an unknown tenant is a genuine routing miss → NOT_FOUND
      if (!knownTenantIds.contains(tenantId)) {
        metrics.recordFailureLatency(latencyTimer);
        return deny(call, Status.NOT_FOUND.withDescription("Unknown physical tenant: " + tenantId));
      }
      metrics.recordSuccessLatency(latencyTimer);
      final Context contextWithTenant =
          Context.current().withValue(InterceptorUtil.getPhysicalTenantIdKey(), tenantId);
      return Contexts.interceptCall(contextWithTenant, call, headers, next);
    }

    // protected API
    final var authorization = headers.get(AUTH_KEY);
    if (authorization == null) {
      metrics.recordFailureLatency(latencyTimer);
      return deny(
          call,
          Status.UNAUTHENTICATED.augmentDescription(
              "Expected authentication information at header with key [%s], but found nothing"
                  .formatted(AUTH_KEY.name())));
    }

    // An unknown tenant on a protected API must not reveal tenant existence to an
    // unauthenticated caller → respond UNAUTHENTICATED without echoing the tenant id.
    if (!knownTenantIds.contains(tenantId)) {
      metrics.recordFailureLatency(latencyTimer);
      return deny(call, Status.UNAUTHENTICATED);
    }

    final var handler = handlersByTenantId.get(tenantId);
    return switch (handler.authenticate(authorization)) {
      case Left<Status, Context>(final var status) -> {
        metrics.recordFailureLatency(latencyTimer);
        yield deny(call, status);
      }
      case Right<Status, Context>(final var context) -> {
        metrics.recordSuccessLatency(latencyTimer);
        final var merged = context.withValue(InterceptorUtil.getPhysicalTenantIdKey(), tenantId);
        yield Contexts.interceptCall(merged, call, headers, next);
      }
    };
  }

  private static <ReqT, RespT> ServerCall.Listener<ReqT> deny(
      final ServerCall<ReqT, RespT> call, final Status status) {
    call.close(status, new Metadata());
    return new Listener<>() {};
  }
}
