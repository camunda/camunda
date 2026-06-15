/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.interceptors.impl;

import static io.camunda.configuration.api.physicaltenants.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;

import io.camunda.configuration.api.physicaltenants.PhysicalTenantIds;
import io.camunda.zeebe.gateway.interceptors.InterceptorUtil;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

/**
 * Reads the {@code Camunda-Physical-Tenant} gRPC header and stores the resolved physical tenant id
 * in the current gRPC {@link Context}. When the header is absent the id defaults to {@link
 * DEFAULT_PHYSICAL_TENANT_ID}.
 *
 * <p>If {@link PhysicalTenantIds} is provided, the resolved id is validated against the set of
 * known tenants; unknown ids are rejected with {@link Status#NOT_FOUND}.
 *
 * <p>Downstream handlers retrieve the value via {@link InterceptorUtil#getPhysicalTenantIdKey()}.
 */
public final class PhysicalTenantInterceptor implements ServerInterceptor {

  static final Metadata.Key<String> PHYSICAL_TENANT_HEADER =
      Metadata.Key.of("Camunda-Physical-Tenant", Metadata.ASCII_STRING_MARSHALLER);

  private final PhysicalTenantIds physicalTenantIds;

  public PhysicalTenantInterceptor(final PhysicalTenantIds physicalTenantIds) {
    this.physicalTenantIds = physicalTenantIds;
  }

  @Override
  public <ReqT, RespT> Listener<ReqT> interceptCall(
      final ServerCall<ReqT, RespT> call,
      final Metadata headers,
      final ServerCallHandler<ReqT, RespT> next) {
    final var tenantId = headers.get(PHYSICAL_TENANT_HEADER);
    final var resolvedTenantId = tenantId != null ? tenantId : DEFAULT_PHYSICAL_TENANT_ID;

    if (!physicalTenantIds.known().contains(resolvedTenantId)) {
      call.close(
          Status.NOT_FOUND.withDescription("Unknown physical tenant: " + resolvedTenantId),
          new Metadata());
      return new ServerCall.Listener<>() {};
    }

    final Context context =
        Context.current().withValue(InterceptorUtil.getPhysicalTenantIdKey(), resolvedTenantId);
    return Contexts.interceptCall(context, call, headers, next);
  }
}
