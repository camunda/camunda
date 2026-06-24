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

import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.security.api.model.config.oidc.OidcConfiguration;
import io.camunda.zeebe.gateway.interceptors.InterceptorUtil;
import io.camunda.zeebe.gateway.protocol.GrpcHeaders;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

final class AuthenticationInterceptorTest {

  private static final Metadata.Key<String> AUTH_KEY =
      Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);

  private final AuthenticationMetrics metrics =
      new AuthenticationMetrics(new SimpleMeterRegistry(), AuthenticationMethod.OIDC);

  @Test
  void shouldStampResolvedTenantIntoContextForUnprotectedApi() {
    // given
    final var interceptor =
        new AuthenticationInterceptor(Set.of("tenant-a"), Map.of(), false, metrics);
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
  void shouldDefaultToDefaultTenantWhenHeaderAbsent() {
    // given
    final var interceptor =
        new AuthenticationInterceptor(Set.of(DEFAULT_PHYSICAL_TENANT_ID), Map.of(), false, metrics);
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
  void shouldRejectUnknownTenantWithNotFound() {
    // given
    final var interceptor =
        new AuthenticationInterceptor(Set.of("tenant-a"), Map.of(), true, metrics);
    final var headers = new Metadata();
    headers.put(GrpcHeaders.PHYSICAL_TENANT, "unknown");
    final var closedCall = new CloseStatusCapturingServerCall<String, String>();

    // when
    interceptor.interceptCall(closedCall, headers, (call, h) -> null);

    // then
    assertThat(closedCall.closeStatus)
        .hasValueSatisfying(
            status -> {
              assertThat(status.getCode()).isEqualTo(Status.NOT_FOUND.getCode());
              assertThat(status.getDescription()).contains("unknown");
            });
  }

  @Test
  void shouldRejectMissingAuthorizationWithUnauthenticatedWhenProtected() {
    // given
    final var interceptor =
        new AuthenticationInterceptor(
            Set.of(DEFAULT_PHYSICAL_TENANT_ID),
            Map.of(DEFAULT_PHYSICAL_TENANT_ID, alwaysAllow()),
            true,
            metrics);
    final var closedCall = new CloseStatusCapturingServerCall<String, String>();

    // when
    interceptor.interceptCall(closedCall, new Metadata(), (call, h) -> null);

    // then
    assertThat(closedCall.closeStatus)
        .hasValueSatisfying(
            status -> assertThat(status.getCode()).isEqualTo(Status.UNAUTHENTICATED.getCode()));
  }

  @Test
  void shouldSelectPerTenantHandlerAndPropagateRejection() {
    // given an allowing handler for tenant-a and a denying one for tenant-b
    final var interceptor =
        new AuthenticationInterceptor(
            Set.of("tenant-a", "tenant-b"),
            Map.of("tenant-a", alwaysAllow(), "tenant-b", alwaysDeny()),
            true,
            metrics);
    final var headers = new Metadata();
    headers.put(GrpcHeaders.PHYSICAL_TENANT, "tenant-b");
    headers.put(AUTH_KEY, "Bearer token");
    final var closedCall = new CloseStatusCapturingServerCall<String, String>();

    // when the denying handler for tenant-b is selected
    interceptor.interceptCall(closedCall, headers, (call, h) -> null);

    // then the handler's rejection status is propagated
    assertThat(closedCall.closeStatus)
        .hasValueSatisfying(
            status -> assertThat(status.getCode()).isEqualTo(Status.UNAUTHENTICATED.getCode()));
  }

  @Test
  void shouldStampTenantAfterSuccessfulAuthentication() {
    // given
    final var interceptor =
        new AuthenticationInterceptor(
            Set.of("tenant-a"), Map.of("tenant-a", alwaysAllow()), true, metrics);
    final var headers = new Metadata();
    headers.put(GrpcHeaders.PHYSICAL_TENANT, "tenant-a");
    headers.put(AUTH_KEY, "Bearer token");
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

  /** A real OIDC handler whose fake decoder yields a token carrying the default username claim. */
  private static AuthenticationHandler alwaysAllow() {
    final var oidcConfig = new OidcConfiguration();
    return new AuthenticationHandler.Oidc(
        token ->
            new Jwt(
                token,
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "none"),
                Map.of(oidcConfig.getUsernameClaim(), "test-user")),
        (jwtClaims, tokenValue) -> jwtClaims,
        oidcConfig);
  }

  /** A real OIDC handler whose decoder always rejects, yielding UNAUTHENTICATED. */
  private static AuthenticationHandler alwaysDeny() {
    return new AuthenticationHandler.Oidc(
        token -> {
          throw new JwtException("rejected");
        },
        (jwtClaims, tokenValue) -> jwtClaims,
        new OidcConfiguration());
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
