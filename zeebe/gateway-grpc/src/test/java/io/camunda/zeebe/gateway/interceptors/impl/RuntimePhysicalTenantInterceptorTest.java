/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.interceptors.impl;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.security.configuration.EngineSecurityConfigurations;
import io.camunda.zeebe.gateway.GatewayPhysicalTenants;
import io.camunda.zeebe.gateway.interceptors.InterceptorUtil;
import io.camunda.zeebe.gateway.protocol.GrpcHeaders;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.Status.Code;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class RuntimePhysicalTenantInterceptorTest {

  private final GatewayPhysicalTenants tenants =
      new GatewayPhysicalTenants(
          Map.of(DEFAULT_PHYSICAL_TENANT_ID, EngineSecurityConfigurations.defaultConfig()),
          false,
          config -> null,
          config -> null,
          tenantId -> null,
          null,
          new SimpleMeterRegistry());

  private final AuthenticationInterceptor interceptor =
      new AuthenticationInterceptor(tenants::authentication, false, new SimpleMeterRegistry());

  @Test
  void shouldServeATenantAddedAfterTheInterceptorWasBuilt() {
    // given
    tenants.put("added", EngineSecurityConfigurations.defaultConfig());

    // when
    final var routedTo = call("added");

    // then
    assertThat(routedTo.get()).isEqualTo("added");
  }

  @Test
  void shouldRejectARestartingTenantAsUnavailable() {
    // given
    tenants.put("restarting", EngineSecurityConfigurations.defaultConfig());
    tenants.markUnavailable("restarting", "its module restarts");
    final var serverCall = new CapturingServerCall();

    // when
    interceptor.interceptCall(serverCall, headersFor("restarting"), (c, h) -> null);

    // then
    assertThat(serverCall.closeStatus.get().getCode()).isEqualTo(Code.UNAVAILABLE);
    assertThat(serverCall.closeStatus.get().getDescription()).contains("its module restarts");
  }

  @Test
  void shouldServeATenantAgainOnceItIsPutAfterARestart() {
    // given
    tenants.put("back", EngineSecurityConfigurations.defaultConfig());
    tenants.markUnavailable("back", "its module restarts");

    // when
    tenants.put("back", EngineSecurityConfigurations.defaultConfig());

    // then
    assertThat(call("back").get()).isEqualTo("back");
  }

  @Test
  void shouldRejectARemovedTenantAsUnknown() {
    // given
    tenants.put("removed", EngineSecurityConfigurations.defaultConfig());
    tenants.remove("removed");
    final var serverCall = new CapturingServerCall();

    // when
    interceptor.interceptCall(serverCall, headersFor("removed"), (c, h) -> null);

    // then
    assertThat(serverCall.closeStatus.get().getCode()).isEqualTo(Code.NOT_FOUND);
  }

  private AtomicReference<String> call(final String physicalTenantId) {
    final var routedTo = new AtomicReference<String>();
    interceptor.interceptCall(
        new NoopServerCall<>() {},
        headersFor(physicalTenantId),
        (c, h) -> {
          routedTo.set(InterceptorUtil.getPhysicalTenantIdKey().get());
          return null;
        });
    return routedTo;
  }

  private static Metadata headersFor(final String physicalTenantId) {
    final var headers = new Metadata();
    headers.put(GrpcHeaders.PHYSICAL_TENANT, physicalTenantId);
    return headers;
  }

  private static final class CapturingServerCall extends NoopServerCall<Object, Object> {
    private final AtomicReference<Status> closeStatus = new AtomicReference<>();

    @Override
    public void close(final Status status, final Metadata trailers) {
      closeStatus.set(status);
    }

    @Override
    @SuppressWarnings("unchecked")
    public MethodDescriptor<Object, Object> getMethodDescriptor() {
      return mock(MethodDescriptor.class);
    }
  }
}
