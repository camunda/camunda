/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.interceptors.impl;

import static io.camunda.configuration.api.physicaltenants.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.configuration.api.physicaltenants.PhysicalTenantIds;
import io.camunda.zeebe.gateway.interceptors.InterceptorUtil;
import io.camunda.zeebe.gateway.protocol.GrpcHeaders;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
final class PhysicalTenantInterceptorTest {

  @Test
  void shouldStoreHeaderValueInContext() {
    // given
    final var interceptor = new PhysicalTenantInterceptor(() -> Set.of("my-tenant"));
    final var headers = new Metadata();
    headers.put(GrpcHeaders.PHYSICAL_TENANT, "my-tenant");
    final AtomicReference<String> captured = new AtomicReference<>();

    // when
    interceptor.interceptCall(
        new NoopServerCall<>() {},
        headers,
        (call, h) -> {
          captured.set(InterceptorUtil.getPhysicalTenantIdKey().get());
          return null;
        });

    // then
    assertThat(captured).hasValue("my-tenant");
  }

  @Test
  void shouldDefaultToDefaultTenantWhenHeaderAbsent() {
    // given
    final var interceptor = new PhysicalTenantInterceptor(PhysicalTenantIds.DEFAULT);
    final AtomicReference<String> captured = new AtomicReference<>();

    // when
    interceptor.interceptCall(
        new NoopServerCall<>() {},
        new Metadata(),
        (call, h) -> {
          captured.set(InterceptorUtil.getPhysicalTenantIdKey().get());
          return null;
        });

    // then
    assertThat(captured).hasValue(DEFAULT_PHYSICAL_TENANT_ID);
  }

  @Test
  void shouldAllowKnownTenant() {
    // given
    final PhysicalTenantIds knownTenants = () -> Set.of("tenant-a", "tenant-b");
    final var interceptor = new PhysicalTenantInterceptor(knownTenants);
    final var headers = new Metadata();
    headers.put(GrpcHeaders.PHYSICAL_TENANT, "tenant-a");
    final AtomicReference<String> captured = new AtomicReference<>();

    // when
    interceptor.interceptCall(
        new NoopServerCall<>() {},
        headers,
        (call, h) -> {
          captured.set(InterceptorUtil.getPhysicalTenantIdKey().get());
          return null;
        });

    // then
    assertThat(captured).hasValue("tenant-a");
  }

  @Test
  void shouldRejectUnknownTenant() {
    // given
    final PhysicalTenantIds knownTenants = () -> Set.of("tenant-a");
    final var interceptor = new PhysicalTenantInterceptor(knownTenants);
    final var headers = new Metadata();
    headers.put(GrpcHeaders.PHYSICAL_TENANT, "unknown-tenant");
    final var closedCall = new CloseStatusCapturingServerCall<String, String>();

    // when
    interceptor.interceptCall(closedCall, headers, (call, h) -> null);

    // then
    assertThat(closedCall.closeStatus)
        .hasValueSatisfying(
            status -> {
              assertThat(status.getCode()).isEqualTo(Status.NOT_FOUND.getCode());
              assertThat(status.getDescription()).contains("unknown-tenant");
            });
  }

  @Test
  void shouldRejectAnyTenantWhenKnownIsEmpty() {
    // given
    final var interceptor = new PhysicalTenantInterceptor(Set::of);
    final var headers = new Metadata();
    headers.put(GrpcHeaders.PHYSICAL_TENANT, "any-tenant");
    final var closedCall = new CloseStatusCapturingServerCall<String, String>();

    // when
    interceptor.interceptCall(closedCall, headers, (call, h) -> null);

    // then
    assertThat(closedCall.closeStatus)
        .hasValueSatisfying(
            status -> {
              assertThat(status.getCode()).isEqualTo(Status.NOT_FOUND.getCode());
              assertThat(status.getDescription()).contains("any-tenant");
            });
  }

  private static final class CloseStatusCapturingServerCall<ReqT, RespT>
      extends NoopServerCall<ReqT, RespT> {
    private final AtomicReference<Status> closeStatus = new AtomicReference<>();

    @Override
    public void close(final Status status, final Metadata trailers) {
      closeStatus.set(status);
    }

    @Override
    @SuppressWarnings("unchecked")
    public MethodDescriptor<ReqT, RespT> getMethodDescriptor() {
      return mock(MethodDescriptor.class);
    }
  }
}
