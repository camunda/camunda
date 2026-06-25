/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.interceptors.impl;

import static io.camunda.configuration.api.physicaltenants.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
import static io.camunda.zeebe.gateway.interceptors.impl.AuthenticationHandler.CLIENT_ID;
import static io.camunda.zeebe.gateway.interceptors.impl.AuthenticationHandler.GROUPS_CLAIMS;
import static io.camunda.zeebe.gateway.interceptors.impl.AuthenticationHandler.USERNAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import io.camunda.search.entities.UserEntity;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.security.api.model.config.oidc.OidcConfiguration;
import io.camunda.service.UserServices;
import io.camunda.zeebe.gateway.interceptors.InterceptorUtil;
import io.camunda.zeebe.gateway.interceptors.impl.AuthenticationHandler.BasicAuth;
import io.camunda.zeebe.gateway.interceptors.impl.AuthenticationHandler.Oidc;
import io.camunda.zeebe.gateway.interceptors.impl.AuthenticationMetricsDoc.AuthResultValues;
import io.camunda.zeebe.gateway.interceptors.impl.AuthenticationMetricsDoc.LatencyKeyNames;
import io.camunda.zeebe.gateway.interceptors.impl.AuthenticationMetricsDoc.RejectionKeyNames;
import io.camunda.zeebe.gateway.protocol.GrpcHeaders;
import io.grpc.Context;
import io.grpc.Metadata;
import io.grpc.Metadata.Key;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.AbstractStringAssert;
import org.assertj.core.api.ListAssert;
import org.assertj.core.api.MapAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

final class AuthenticationInterceptorTest {

  private static final Metadata.Key<String> AUTH_KEY =
      Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);

  @Mock private UserServices userServices;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private JwtDecoder jwtDecoder;

  private AuthenticationMetrics metrics;
  private SimpleMeterRegistry meterRegistry;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    meterRegistry = new SimpleMeterRegistry();
    metrics = new AuthenticationMetrics(meterRegistry, AuthenticationMethod.OIDC);
  }

  // Auth behavior tests (BASIC + OIDC)

  @Test
  void missingAuthenticationInformationIsRejected() {
    // given
    final var interceptor =
        new AuthenticationInterceptor(
            Set.of(DEFAULT_PHYSICAL_TENANT_ID),
            Map.of(DEFAULT_PHYSICAL_TENANT_ID, alwaysAllow()),
            true,
            Map.of(DEFAULT_PHYSICAL_TENANT_ID, metrics),
            meterRegistry);
    final var closedCall = new CloseStatusCapturingServerCall<Object, Object>();

    // when
    interceptor.interceptCall(closedCall, new Metadata(), failingNextHandler());

    // then
    assertThat(closedCall.closeStatus)
        .hasValueSatisfying(
            status -> {
              assertThat(status.getCode()).isEqualTo(Status.UNAUTHENTICATED.getCode());
              assertThat(status.getDescription())
                  .isEqualTo(
                      "Expected authentication information at header with key [authorization], but found nothing");
            });
  }

  @Test
  void validBasicAuthIsAccepted() {
    // given
    final var metadata = new Metadata();
    // demo:demo
    metadata.put(Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER), "Basic ZGVtbzpkZW1v");
    when(userServices.search(any(), any()))
        .thenReturn(new SearchQueryResult<>(1, false, List.of(createUserEntity()), null, null));
    when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
    final var interceptor =
        new AuthenticationInterceptor(
            Set.of(DEFAULT_PHYSICAL_TENANT_ID),
            Map.of(DEFAULT_PHYSICAL_TENANT_ID, new BasicAuth(userServices, passwordEncoder)),
            true,
            Map.of(DEFAULT_PHYSICAL_TENANT_ID, metrics),
            meterRegistry);
    final var closedCall = new CloseStatusCapturingServerCall<Object, Object>();

    // when
    interceptor.interceptCall(
        closedCall,
        metadata,
        (call, headers) -> {
          call.close(Status.OK, headers);
          return null;
        });

    // then
    assertThat(closedCall.closeStatus).hasValue(Status.OK);
  }

  @Test
  void addUserKeyToContext() {
    // given
    final var metadata = new Metadata();
    // demo:demo
    metadata.put(Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER), "Basic ZGVtbzpkZW1v");
    when(userServices.search(any(), any()))
        .thenReturn(new SearchQueryResult<>(1, false, List.of(createUserEntity()), null, null));
    when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
    final var interceptor =
        new AuthenticationInterceptor(
            Set.of(DEFAULT_PHYSICAL_TENANT_ID),
            Map.of(DEFAULT_PHYSICAL_TENANT_ID, new BasicAuth(userServices, passwordEncoder)),
            true,
            Map.of(DEFAULT_PHYSICAL_TENANT_ID, metrics),
            meterRegistry);
    final var closedCall = new CloseStatusCapturingServerCall<Object, Object>();

    // when
    interceptor.interceptCall(
        closedCall,
        metadata,
        (call, headers) -> {
          // then
          assertUsername().isEqualTo("demo");
          call.close(Status.OK, headers);
          return null;
        });

    // then
    assertThat(closedCall.closeStatus).hasValue(Status.OK);
  }

  @Test
  void missingUserIsRejected() {
    // given
    final var metadata = new Metadata();
    // demo:demo
    metadata.put(Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER), "Basic ZGVtbzpkZW1v");
    when(userServices.search(any(), any()))
        .thenReturn(new SearchQueryResult<>(0, false, List.of(), null, null));
    final var interceptor =
        new AuthenticationInterceptor(
            Set.of(DEFAULT_PHYSICAL_TENANT_ID),
            Map.of(DEFAULT_PHYSICAL_TENANT_ID, new BasicAuth(userServices, passwordEncoder)),
            true,
            Map.of(DEFAULT_PHYSICAL_TENANT_ID, metrics),
            meterRegistry);
    final var closedCall = new CloseStatusCapturingServerCall<Object, Object>();

    // when
    interceptor.interceptCall(closedCall, metadata, failingNextHandler());

    // then
    assertThat(closedCall.closeStatus)
        .hasValueSatisfying(
            status -> {
              assertThat(status.getCode()).isEqualTo(Status.UNAUTHENTICATED.getCode());
              assertThat(status.getDescription()).isEqualTo("Invalid credentials");
            });
  }

  @Test
  void notValidPasswordIsRejected() {
    // given
    final var metadata = new Metadata();
    // demo:demo
    metadata.put(Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER), "Basic ZGVtbzpkZW1v");
    when(userServices.search(any(), any()))
        .thenReturn(new SearchQueryResult<>(1, false, List.of(createUserEntity()), null, null));
    when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);
    final var interceptor =
        new AuthenticationInterceptor(
            Set.of(DEFAULT_PHYSICAL_TENANT_ID),
            Map.of(DEFAULT_PHYSICAL_TENANT_ID, new BasicAuth(userServices, passwordEncoder)),
            true,
            Map.of(DEFAULT_PHYSICAL_TENANT_ID, metrics),
            meterRegistry);
    final var closedCall = new CloseStatusCapturingServerCall<Object, Object>();

    // when
    interceptor.interceptCall(closedCall, metadata, failingNextHandler());

    // then
    assertThat(closedCall.closeStatus)
        .hasValueSatisfying(
            status -> {
              assertThat(status.getCode()).isEqualTo(Status.UNAUTHENTICATED.getCode());
              assertThat(status.getDescription()).isEqualTo("Invalid credentials");
            });
  }

  @Test
  void validTokenIsAccepted() {
    // given
    final var jwt = mock(org.springframework.security.oauth2.jwt.Jwt.class);
    final Map<String, Object> claims =
        Map.of(
            "role", "admin",
            "foo", "bar",
            "baz", "qux",
            "username", "test-user");
    when(jwt.getClaims()).thenReturn(claims);
    when(jwtDecoder.decode(anyString())).thenReturn(jwt);
    final var oidcConfig = new OidcConfiguration();
    oidcConfig.setUsernameClaim("username");
    final var interceptor =
        new AuthenticationInterceptor(
            Set.of(DEFAULT_PHYSICAL_TENANT_ID),
            Map.of(
                DEFAULT_PHYSICAL_TENANT_ID,
                new Oidc(jwtDecoder, (jwtClaims, tokenValue) -> jwtClaims, oidcConfig)),
            true,
            Map.of(DEFAULT_PHYSICAL_TENANT_ID, metrics),
            meterRegistry);
    final var closedCall = new CloseStatusCapturingServerCall<Object, Object>();

    // when
    interceptor.interceptCall(
        closedCall,
        createAuthHeader(),
        (call, headers) -> {
          call.close(Status.OK, headers);
          return null;
        });

    // then
    assertThat(closedCall.closeStatus).hasValue(Status.OK);
  }

  @Test
  void missingUsernameAndClientIdIsRejected() {
    // given
    final var jwt = mock(org.springframework.security.oauth2.jwt.Jwt.class);
    final Map<String, Object> claims = Map.of("username", "test-user");
    when(jwt.getClaims()).thenReturn(claims);
    when(jwtDecoder.decode(anyString())).thenReturn(jwt);
    final var oidcConfig = new OidcConfiguration();
    oidcConfig.setUsernameClaim("sub");
    oidcConfig.setClientIdClaim("client_id");
    final var interceptor =
        new AuthenticationInterceptor(
            Set.of(DEFAULT_PHYSICAL_TENANT_ID),
            Map.of(
                DEFAULT_PHYSICAL_TENANT_ID,
                new Oidc(jwtDecoder, (jwtClaims, tokenValue) -> jwtClaims, oidcConfig)),
            true,
            Map.of(DEFAULT_PHYSICAL_TENANT_ID, metrics),
            meterRegistry);
    final var closedCall = new CloseStatusCapturingServerCall<Object, Object>();

    // when
    interceptor.interceptCall(
        closedCall,
        createAuthHeader(),
        (call, headers) -> {
          call.close(Status.OK, headers);
          return null;
        });

    // then
    assertThat(closedCall.closeStatus)
        .hasValueSatisfying(
            status -> {
              assertThat(status.getCode()).isEqualTo(Status.UNAUTHENTICATED.getCode());
              assertThat(status.getDescription())
                  .isEqualTo(
                      "Expected either a username (claim: sub) or client ID (claim: client_id) on the token, but no matching claim found");
            });
  }

  @Test
  void nonStringUsernameIsRejected() {
    // given
    final var jwt = mock(org.springframework.security.oauth2.jwt.Jwt.class);
    final Map<String, Object> claims = Map.of("username", List.of("test-user"));
    when(jwt.getClaims()).thenReturn(claims);
    when(jwtDecoder.decode(anyString())).thenReturn(jwt);
    final var oidcConfig = new OidcConfiguration();
    oidcConfig.setUsernameClaim("username");
    oidcConfig.setClientIdClaim("application_id");
    final var interceptor =
        new AuthenticationInterceptor(
            Set.of(DEFAULT_PHYSICAL_TENANT_ID),
            Map.of(
                DEFAULT_PHYSICAL_TENANT_ID,
                new Oidc(jwtDecoder, (jwtClaims, tokenValue) -> jwtClaims, oidcConfig)),
            true,
            Map.of(DEFAULT_PHYSICAL_TENANT_ID, metrics),
            meterRegistry);
    final var closedCall = new CloseStatusCapturingServerCall<Object, Object>();

    // when
    interceptor.interceptCall(
        closedCall,
        createAuthHeader(),
        (call, headers) -> {
          call.close(Status.OK, headers);
          return null;
        });

    // then
    assertThat(closedCall.closeStatus)
        .hasValueSatisfying(
            status -> {
              assertThat(status.getCode()).isEqualTo(Status.UNAUTHENTICATED.getCode());
              assertThat(status.getDescription()).isEqualTo("Failed to load OIDC principals");
              assertThat(status.getCause()).isNull();
            });
  }

  @Test
  void nonStringClientIdIsRejected() {
    // given
    final var jwt = mock(org.springframework.security.oauth2.jwt.Jwt.class);
    final Map<String, Object> claims = Map.of("client_id", List.of("test-user"));
    when(jwt.getClaims()).thenReturn(claims);
    when(jwtDecoder.decode(anyString())).thenReturn(jwt);
    final var oidcConfig = new OidcConfiguration();
    oidcConfig.setUsernameClaim("username");
    oidcConfig.setClientIdClaim("client_id");
    final var interceptor =
        new AuthenticationInterceptor(
            Set.of(DEFAULT_PHYSICAL_TENANT_ID),
            Map.of(
                DEFAULT_PHYSICAL_TENANT_ID,
                new Oidc(jwtDecoder, (jwtClaims, tokenValue) -> jwtClaims, oidcConfig)),
            true,
            Map.of(DEFAULT_PHYSICAL_TENANT_ID, metrics),
            meterRegistry);
    final var closedCall = new CloseStatusCapturingServerCall<Object, Object>();

    // when
    interceptor.interceptCall(
        closedCall,
        createAuthHeader(),
        (call, headers) -> {
          call.close(Status.OK, headers);
          return null;
        });

    // then
    assertThat(closedCall.closeStatus)
        .hasValueSatisfying(
            status -> {
              assertThat(status.getCode()).isEqualTo(Status.UNAUTHENTICATED.getCode());
              assertThat(status.getDescription()).isEqualTo("Failed to load OIDC principals");
              assertThat(status.getCause()).isNull();
            });
  }

  @Test
  void addsUserClaimsToContext() {
    // given
    final var metadata = createAuthHeader();
    final var jwt = mock(org.springframework.security.oauth2.jwt.Jwt.class);
    final Map<String, Object> claims =
        Map.of(
            "role", "admin",
            "foo", "bar",
            "baz", "qux",
            "username", "test-user");
    when(jwt.getClaims()).thenReturn(claims);
    when(jwtDecoder.decode(anyString())).thenReturn(jwt);
    final var oidcConfig = new OidcConfiguration();
    oidcConfig.setUsernameClaim("username");
    oidcConfig.setClientIdClaim("application_id");
    final var interceptor =
        new AuthenticationInterceptor(
            Set.of(DEFAULT_PHYSICAL_TENANT_ID),
            Map.of(
                DEFAULT_PHYSICAL_TENANT_ID,
                new Oidc(jwtDecoder, (jwtClaims, tokenValue) -> jwtClaims, oidcConfig)),
            true,
            Map.of(DEFAULT_PHYSICAL_TENANT_ID, metrics),
            meterRegistry);
    final var closedCall = new CloseStatusCapturingServerCall<Object, Object>();

    // when
    interceptor.interceptCall(
        closedCall,
        metadata,
        (call, headers) -> {
          // then
          assertUserClaims().containsKey("role").containsKey("foo").containsKey("baz");
          call.close(Status.OK, headers);
          return null;
        });
  }

  @Test
  void addsClientIdInOidcToContext() {
    // given
    final var metadata = createAuthHeader();
    final var jwt = mock(org.springframework.security.oauth2.jwt.Jwt.class);
    final Map<String, Object> claims = Map.of("untested", "test-user", "application_id", "app-id");
    when(jwt.getClaims()).thenReturn(claims);
    when(jwtDecoder.decode(anyString())).thenReturn(jwt);
    final var oidcConfig = new OidcConfiguration();
    oidcConfig.setUsernameClaim("username");
    oidcConfig.setClientIdClaim("application_id");
    final var interceptor =
        new AuthenticationInterceptor(
            Set.of(DEFAULT_PHYSICAL_TENANT_ID),
            Map.of(
                DEFAULT_PHYSICAL_TENANT_ID,
                new Oidc(jwtDecoder, (jwtClaims, tokenValue) -> jwtClaims, oidcConfig)),
            true,
            Map.of(DEFAULT_PHYSICAL_TENANT_ID, metrics),
            meterRegistry);
    final var closedCall = new CloseStatusCapturingServerCall<Object, Object>();

    // when
    interceptor.interceptCall(
        closedCall,
        metadata,
        (call, headers) -> {
          // then
          assertClientId().isEqualTo("app-id");
          call.close(Status.OK, headers);
          return null;
        });
  }

  @Test
  void
      addsUsernameToOidcContextWhenPreferUsernameClaimIsTrueWhenUsernameAndClientIdClaimsArePresent() {
    // given
    final var metadata = createAuthHeader();
    final var jwt = mock(org.springframework.security.oauth2.jwt.Jwt.class);
    final Map<String, Object> claims = Map.of("username", "test-user", "application_id", "app-id");
    when(jwt.getClaims()).thenReturn(claims);
    when(jwtDecoder.decode(anyString())).thenReturn(jwt);
    final var oidcConfig = new OidcConfiguration();
    oidcConfig.setUsernameClaim("username");
    oidcConfig.setClientIdClaim("application_id");
    oidcConfig.setPreferUsernameClaim(true);
    final var interceptor =
        new AuthenticationInterceptor(
            Set.of(DEFAULT_PHYSICAL_TENANT_ID),
            Map.of(
                DEFAULT_PHYSICAL_TENANT_ID,
                new Oidc(jwtDecoder, (jwtClaims, tokenValue) -> jwtClaims, oidcConfig)),
            true,
            Map.of(DEFAULT_PHYSICAL_TENANT_ID, metrics),
            meterRegistry);
    final var closedCall = new CloseStatusCapturingServerCall<Object, Object>();

    // when
    interceptor.interceptCall(
        closedCall,
        metadata,
        (call, headers) -> {
          // then
          assertUsername().isEqualTo("test-user");
          assertClientId().isNull();
          call.close(Status.OK, headers);
          return null;
        });
  }

  @Test
  void
      addsClientIdToOidcContextWhenPreferUsernameClaimIsFalseWhenUsernameAndClientIdClaimsArePresent() {
    // given
    final var metadata = createAuthHeader();
    final var jwt = mock(org.springframework.security.oauth2.jwt.Jwt.class);
    final Map<String, Object> claims = Map.of("username", "test-user", "application_id", "app-id");
    when(jwt.getClaims()).thenReturn(claims);
    when(jwtDecoder.decode(anyString())).thenReturn(jwt);
    final var oidcConfig = new OidcConfiguration();
    oidcConfig.setUsernameClaim("username");
    oidcConfig.setClientIdClaim("application_id");
    final var interceptor =
        new AuthenticationInterceptor(
            Set.of(DEFAULT_PHYSICAL_TENANT_ID),
            Map.of(
                DEFAULT_PHYSICAL_TENANT_ID,
                new Oidc(jwtDecoder, (jwtClaims, tokenValue) -> jwtClaims, oidcConfig)),
            true,
            Map.of(DEFAULT_PHYSICAL_TENANT_ID, metrics),
            meterRegistry);
    final var closedCall = new CloseStatusCapturingServerCall<Object, Object>();

    // when
    interceptor.interceptCall(
        closedCall,
        metadata,
        (call, headers) -> {
          // then
          assertUsername().isNull();
          assertClientId().isEqualTo("app-id");
          call.close(Status.OK, headers);
          return null;
        });
  }

  @Test
  void addsGroupsInOidcToContext() {
    // given
    final var metadata = createAuthHeader();
    final var jwt = mock(org.springframework.security.oauth2.jwt.Jwt.class);
    final Map<String, Object> claims =
        Map.of("username", "test-user", "groups", List.of("foo", "bar"));
    when(jwt.getClaims()).thenReturn(claims);
    when(jwtDecoder.decode(anyString())).thenReturn(jwt);
    final var oidcConfig = new OidcConfiguration();
    oidcConfig.setUsernameClaim("username");
    oidcConfig.setClientIdClaim("application_id");
    oidcConfig.setGroupsClaim("$.groups[*]");
    final var interceptor =
        new AuthenticationInterceptor(
            Set.of(DEFAULT_PHYSICAL_TENANT_ID),
            Map.of(
                DEFAULT_PHYSICAL_TENANT_ID,
                new Oidc(jwtDecoder, (jwtClaims, tokenValue) -> jwtClaims, oidcConfig)),
            true,
            Map.of(DEFAULT_PHYSICAL_TENANT_ID, metrics),
            meterRegistry);
    final var closedCall = new CloseStatusCapturingServerCall<Object, Object>();

    // when
    interceptor.interceptCall(
        closedCall,
        metadata,
        (call, headers) -> {
          // then
          assertGroups().containsExactlyInAnyOrder("foo", "bar");
          call.close(Status.OK, headers);
          return null;
        });
  }

  @Test
  void nonStringArrayGroupsIsRejected() {
    // given
    final var jwt = mock(org.springframework.security.oauth2.jwt.Jwt.class);
    final Map<String, Object> claims =
        Map.of("username", "test-user", "groups", List.of(Map.of("foo", "bar")));
    when(jwt.getClaims()).thenReturn(claims);
    when(jwtDecoder.decode(anyString())).thenReturn(jwt);
    final var oidcConfig = new OidcConfiguration();
    oidcConfig.setUsernameClaim("username");
    oidcConfig.setClientIdClaim("client_id");
    oidcConfig.setGroupsClaim("$.groups[*]");
    final var interceptor =
        new AuthenticationInterceptor(
            Set.of(DEFAULT_PHYSICAL_TENANT_ID),
            Map.of(
                DEFAULT_PHYSICAL_TENANT_ID,
                new Oidc(jwtDecoder, (jwtClaims, tokenValue) -> jwtClaims, oidcConfig)),
            true,
            Map.of(DEFAULT_PHYSICAL_TENANT_ID, metrics),
            meterRegistry);
    final var closedCall = new CloseStatusCapturingServerCall<Object, Object>();

    // when
    interceptor.interceptCall(
        closedCall,
        createAuthHeader(),
        (call, headers) -> {
          call.close(Status.OK, headers);
          return null;
        });

    // then
    assertThat(closedCall.closeStatus)
        .hasValueSatisfying(
            status -> {
              assertThat(status.getCode()).isEqualTo(Status.UNAUTHENTICATED.getCode());
              assertThat(status.getDescription()).isEqualTo("Failed to load OIDC groups");
              assertThat(status.getCause()).isNull();
            });
  }

  @Test
  void shouldMeasureLatency() {
    // given
    final var meterRegistry = new SimpleMeterRegistry();
    final var basicMetrics = new AuthenticationMetrics(meterRegistry, AuthenticationMethod.BASIC);
    final var capturingCall = new CloseStatusCapturingServerCall<Object, Object>();
    final var metadata = new Metadata();
    // demo:demo
    metadata.put(Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER), "Basic ZGVtbzpkZW1v");
    when(userServices.search(any(), any()))
        .thenReturn(new SearchQueryResult<>(1, false, List.of(createUserEntity()), null, null));
    when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
    final var interceptor =
        new AuthenticationInterceptor(
            Set.of(DEFAULT_PHYSICAL_TENANT_ID),
            Map.of(DEFAULT_PHYSICAL_TENANT_ID, new BasicAuth(userServices, passwordEncoder)),
            true,
            Map.of(DEFAULT_PHYSICAL_TENANT_ID, basicMetrics),
            meterRegistry);

    // when
    interceptor.interceptCall(
        capturingCall,
        metadata,
        (call, headers) -> {
          call.close(Status.OK, headers);
          return null;
        });

    // then
    final var timer =
        meterRegistry
            .get(AuthenticationMetricsDoc.LATENCY.getName())
            .tag(LatencyKeyNames.AUTH_METHOD.asString(), "BASIC")
            .tag(LatencyKeyNames.AUTH_RESULT.asString(), AuthResultValues.SUCCESS.getValue())
            .timer();
    assertThat(timer.count()).isOne();
    assertThat(timer.mean(TimeUnit.MILLISECONDS)).isGreaterThan(0);
    assertThat(capturingCall.closeStatus.get().getCode()).isEqualTo(Status.OK.getCode());
  }

  @Test
  void shouldMeasureFailureLatencyWhenCredentialsAreInvalid() {
    // given
    final var meterRegistry = new SimpleMeterRegistry();
    final var basicMetrics = new AuthenticationMetrics(meterRegistry, AuthenticationMethod.BASIC);
    final var capturingCall = new CloseStatusCapturingServerCall<Object, Object>();
    final var metadata = new Metadata();
    // demo:demo
    metadata.put(Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER), "Basic ZGVtbzpkZW1v");
    when(userServices.search(any(), any()))
        .thenReturn(new SearchQueryResult<>(0, false, List.of(), null, null));
    final var interceptor =
        new AuthenticationInterceptor(
            Set.of(DEFAULT_PHYSICAL_TENANT_ID),
            Map.of(DEFAULT_PHYSICAL_TENANT_ID, new BasicAuth(userServices, passwordEncoder)),
            true,
            Map.of(DEFAULT_PHYSICAL_TENANT_ID, basicMetrics),
            meterRegistry);

    // when
    interceptor.interceptCall(
        capturingCall,
        metadata,
        (call, headers) -> {
          call.close(Status.OK, headers);
          return null;
        });

    // then
    final var timer =
        meterRegistry
            .get(AuthenticationMetricsDoc.LATENCY.getName())
            .tag(LatencyKeyNames.AUTH_METHOD.asString(), "BASIC")
            .tag(LatencyKeyNames.AUTH_RESULT.asString(), AuthResultValues.FAILURE.getValue())
            .timer();
    assertThat(timer.count()).isOne();
    assertThat(timer.mean(TimeUnit.MILLISECONDS)).isGreaterThan(0);
    assertThat(capturingCall.closeStatus.get().getCode())
        .isEqualTo(Status.UNAUTHENTICATED.getCode());
  }

  // PT-dispatch tests

  @Test
  void shouldStampResolvedTenantIntoContextForUnprotectedApi() {
    // given
    final var interceptor =
        new AuthenticationInterceptor(Set.of("tenant-a"), Map.of(), false, Map.of(), meterRegistry);
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
        new AuthenticationInterceptor(
            Set.of(DEFAULT_PHYSICAL_TENANT_ID), Map.of(), false, Map.of(), meterRegistry);
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
        new AuthenticationInterceptor(Set.of("tenant-a"), Map.of(), false, Map.of(), meterRegistry);
    final var headers = new Metadata();
    headers.put(GrpcHeaders.PHYSICAL_TENANT, "unknown");
    final var closedCall = new CloseStatusCapturingServerCall<Object, Object>();

    // when
    interceptor.interceptCall(closedCall, headers, (call, h) -> null);

    // then
    assertThat(closedCall.closeStatus)
        .hasValueSatisfying(
            status -> {
              assertThat(status.getCode()).isEqualTo(Status.NOT_FOUND.getCode());
              assertThat(status.getDescription()).contains("unknown");
            });
    assertThat(
            meterRegistry
                .get("zeebe.gateway.grpc.auth.rejected")
                .tag(RejectionKeyNames.REASON.asString(), "unknown_tenant")
                .counter()
                .count())
        .isEqualTo(1.0);
  }

  @Test
  void shouldRejectUnknownTenantWithUnauthenticatedWhenProtected() {
    // given
    final var interceptor =
        new AuthenticationInterceptor(
            Set.of("tenant-a"), Map.of(), true, Map.of("tenant-a", metrics), meterRegistry);
    final var headers = new Metadata();
    headers.put(GrpcHeaders.PHYSICAL_TENANT, "unknown");
    headers.put(AUTH_KEY, "Basic ZGVtbzpkZW1v");
    final var closedCall = new CloseStatusCapturingServerCall<Object, Object>();

    // when
    interceptor.interceptCall(closedCall, headers, (call, h) -> null);

    // then
    assertThat(closedCall.closeStatus)
        .hasValueSatisfying(
            status -> assertThat(status.getCode()).isEqualTo(Status.UNAUTHENTICATED.getCode()));
    assertThat(
            meterRegistry
                .get("zeebe.gateway.grpc.auth.rejected")
                .tag(RejectionKeyNames.REASON.asString(), "unknown_tenant")
                .counter()
                .count())
        .isEqualTo(1.0);
  }

  @Test
  void shouldRejectAnyTenantWhenKnownSetIsEmptyAndUnprotected() {
    // given
    final var interceptor =
        new AuthenticationInterceptor(Set.of(), Map.of(), false, Map.of(), meterRegistry);
    final var headers = new Metadata();
    headers.put(GrpcHeaders.PHYSICAL_TENANT, "any-tenant");
    final var closedCall = new CloseStatusCapturingServerCall<Object, Object>();

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

  @Test
  void shouldRejectMissingAuthorizationWithUnauthenticatedWhenProtected() {
    // given
    final var interceptor =
        new AuthenticationInterceptor(
            Set.of(DEFAULT_PHYSICAL_TENANT_ID),
            Map.of(DEFAULT_PHYSICAL_TENANT_ID, alwaysAllow()),
            true,
            Map.of(DEFAULT_PHYSICAL_TENANT_ID, metrics),
            meterRegistry);
    final var closedCall = new CloseStatusCapturingServerCall<Object, Object>();

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
            Map.of("tenant-a", metrics, "tenant-b", metrics),
            meterRegistry);
    final var headers = new Metadata();
    headers.put(GrpcHeaders.PHYSICAL_TENANT, "tenant-b");
    headers.put(AUTH_KEY, "Bearer token");
    final var closedCall = new CloseStatusCapturingServerCall<Object, Object>();

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
            Set.of("tenant-a"),
            Map.of("tenant-a", alwaysAllow()),
            true,
            Map.of("tenant-a", metrics),
            meterRegistry);
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
    return new Oidc(
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
    return new Oidc(
        token -> {
          throw new JwtException("rejected");
        },
        (jwtClaims, tokenValue) -> jwtClaims,
        new OidcConfiguration());
  }

  private Metadata createAuthHeader() {
    final Metadata metadata = new Metadata();
    metadata.put(
        Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer " + generateToken());
    return metadata;
  }

  private String generateToken() {
    final Algorithm algorithm = Algorithm.HMAC256("secret-key");
    return JWT.create()
        .withIssuer("test-issuer")
        .withSubject("test-user")
        .withAudience("test-audience")
        .withClaim("role", "admin")
        .withClaim("foo", "bar")
        .withClaim("baz", "qux")
        .withExpiresAt(new Date(System.currentTimeMillis() + 60 * 60 * 1000))
        .sign(algorithm);
  }

  @SuppressWarnings("unchecked")
  private static <ReqT, RespT> ServerCallHandler<ReqT, RespT> failingNextHandler() {
    return (call, headers) -> {
      throw new AssertionError("should not reach next handler");
    };
  }

  private static UserEntity createUserEntity() {
    return new UserEntity(1L, "demo", "Demo", "demo@demo.com", "demo");
  }

  private static MapAssert<String, Object> assertUserClaims() {
    try {
      return assertThat(Context.current().call(() -> Oidc.USER_CLAIMS.get()));
    } catch (final Exception e) {
      throw new RuntimeException("Unable to retrieve user claims from context", e);
    }
  }

  private static AbstractStringAssert<?> assertUsername() {
    try {
      return assertThat(Context.current().call(() -> (String) USERNAME.get()));
    } catch (final Exception e) {
      throw new RuntimeException("Unable to retrieve username from context", e);
    }
  }

  private static AbstractStringAssert<?> assertClientId() {
    try {
      return assertThat(Context.current().call(() -> (String) CLIENT_ID.get()));
    } catch (final Exception e) {
      throw new RuntimeException("Unable to retrieve client_id from context", e);
    }
  }

  private static ListAssert<String> assertGroups() {
    try {
      return assertThat(Context.current().call(() -> GROUPS_CLAIMS.get()));
    } catch (final Exception e) {
      throw new RuntimeException("Unable to retrieve groups from context", e);
    }
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
