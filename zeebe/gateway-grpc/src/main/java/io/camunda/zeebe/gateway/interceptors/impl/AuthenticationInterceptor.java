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
import io.camunda.zeebe.gateway.interceptors.impl.AuthenticationMetricsDoc.RejectionKeyNames;
import io.camunda.zeebe.gateway.interceptors.impl.AuthenticationMetricsDoc.RejectionReasonValues;
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
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.Objects;
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
  private final Map<String, AuthenticationMetrics> metricsByTenantId;
  private final Counter unknownTenantRejected;

  public AuthenticationInterceptor(
      final Set<String> knownTenantIds,
      final Map<String, AuthenticationHandler> handlersByTenantId,
      final boolean authEnabled,
      final Map<String, AuthenticationMetrics> metricsByTenantId,
      final MeterRegistry meterRegistry) {
    this.knownTenantIds = Set.copyOf(knownTenantIds);
    this.handlersByTenantId = Map.copyOf(handlersByTenantId);
    this.authEnabled = authEnabled;
    this.metricsByTenantId = Map.copyOf(metricsByTenantId);
    unknownTenantRejected =
        Counter.builder(AuthenticationMetricsDoc.REJECTED.getName())
            .description(AuthenticationMetricsDoc.REJECTED.getDescription())
            .tag(
                RejectionKeyNames.REASON.asString(),
                RejectionReasonValues.UNKNOWN_TENANT.getValue())
            .register(meterRegistry);
  }

  @Override
  public <ReqT, RespT> Listener<ReqT> interceptCall(
      final ServerCall<ReqT, RespT> call,
      final Metadata headers,
      final ServerCallHandler<ReqT, RespT> next) {
    final var tenantHeaderValue = headers.get(GrpcHeaders.PHYSICAL_TENANT);
    final var tenantId = tenantHeaderValue != null ? tenantHeaderValue : DEFAULT_PHYSICAL_TENANT_ID;

    if (!authEnabled) {
      // unprotected API: an unknown tenant is a genuine routing miss → NOT_FOUND
      if (!knownTenantIds.contains(tenantId)) {
        unknownTenantRejected.increment();
        return deny(call, Status.NOT_FOUND.withDescription("Unknown physical tenant: " + tenantId));
      }
      final Context contextWithTenant =
          Context.current().withValue(InterceptorUtil.getPhysicalTenantIdKey(), tenantId);
      return Contexts.interceptCall(contextWithTenant, call, headers, next);
    }

    // protected API. An unknown tenant must not reveal tenant existence to an unauthenticated
    // caller → respond UNAUTHENTICATED without echoing the tenant id.
    if (!knownTenantIds.contains(tenantId)) {
      unknownTenantRejected.increment();
      return deny(call, Status.UNAUTHENTICATED);
    }

    // Record latency against the resolved tenant's metrics so the auth-method dimension reflects
    // that tenant's configured method.
    final var metrics =
        Objects.requireNonNull(
            metricsByTenantId.get(tenantId),
            "No authentication metrics registered for physical tenant: " + tenantId);
    final var latencyTimer = metrics.startLatencySample();

    final var authorization = headers.get(AUTH_KEY);
    if (authorization == null) {
      metrics.recordFailureLatency(latencyTimer);
      return deny(
          call,
          Status.UNAUTHENTICATED.augmentDescription(
              "Expected authentication information at header with key [%s], but found nothing"
                  .formatted(AUTH_KEY.name())));
    }

    final var handler =
        Objects.requireNonNull(
            handlersByTenantId.get(tenantId),
            "No authentication handler registered for physical tenant: " + tenantId);
    return switch (handler.authenticate(authorization)) {
      case Left<Status, Context>(final var status) -> {
        metrics.recordFailureLatency(latencyTimer);
        yield deny(call, status);
      }
      case Right<Status, Context>(final var authContext) -> {
        metrics.recordSuccessLatency(latencyTimer);
        final var merged =
            authContext.withValue(InterceptorUtil.getPhysicalTenantIdKey(), tenantId);
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
