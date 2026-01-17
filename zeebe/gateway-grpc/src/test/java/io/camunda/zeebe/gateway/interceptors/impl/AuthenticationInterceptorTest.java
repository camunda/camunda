/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.interceptors.impl;

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
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.configuration.OidcAuthenticationConfiguration;
import io.camunda.security.entity.AuthenticationMethod;
import io.camunda.service.UserServices;
import io.camunda.zeebe.gateway.interceptors.impl.AuthenticationHandler.BasicAuth;
import io.camunda.zeebe.gateway.interceptors.impl.AuthenticationHandler.Oidc;
import io.camunda.zeebe.gateway.interceptors.impl.AuthenticationMetricsDoc.AuthResultValues;
import io.camunda.zeebe.gateway.interceptors.impl.AuthenticationMetricsDoc.LatencyKeyNames;
import io.grpc.Context;
import io.grpc.Metadata;
import io.grpc.Metadata.Key;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.ListAssert;
import org.assertj.core.api.MapAssert;
import org.assertj.core.api.StringAssert;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;

public class AuthenticationInterceptorTest {

  @Test
  public void missingAuthenticationInformationIsRejected() {
    // when
    final CloseStatusCapturingServerCall closeStatusCapturingServerCall =
        new CloseStatusCapturingServerCall();
    new AuthenticationInterceptor(null)
        .interceptCall(closeStatusCapturingServerCall, new Metadata(), failingNextHandler());

    // then
    assertThat(closeStatusCapturingServerCall.closeStatus)
        .hasValueSatisfying(
            status -> {
              assertThat(status.getCode()).isEqualTo(Status.UNAUTHENTICATED.getCode());
              assertThat(status.getDescription())
                  .isEqualTo(
                      "Expected authentication information at header with key [authorization], but found nothing");
            });
  }

  // BASIC AUTH tests
  @Test
  public void validBasicAuthIsAccepted() {
    // when
    final CloseStatusCapturingServerCall closeStatusCapturingServerCall =
        new CloseStatusCapturingServerCall();
    final Metadata metadata = new Metadata();
    // demo:demo
    metadata.put(Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER), "Basic ZGVtbzpkZW1v");
    final var userServices = mock(UserServices.class);
    when(userServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(userServices);
    when(userServices.search(any()))
        .thenReturn(new SearchQueryResult<>(1, false, List.of(createUserEntity()), null, null));
    final var passwordEncoder = mock(PasswordEncoder.class);
    when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
    new AuthenticationInterceptor(new BasicAuth(userServices, passwordEncoder))
        .interceptCall(
            closeStatusCapturingServerCall,
            metadata,
            (call, headers) -> {
              call.close(Status.OK, headers);
              return null;
            });

    // then
    assertThat(closeStatusCapturingServerCall.closeStatus).hasValue(Status.OK);
  }

  @Test
  public void addUserKeyToContext() {
    // given
    final CloseStatusCapturingServerCall closeStatusCapturingServerCall =
        new CloseStatusCapturingServerCall();
    final Metadata metadata = new Metadata();
    // demo:demo
    metadata.put(Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER), "Basic ZGVtbzpkZW1v");
    final var userServices = mock(UserServices.class);
    when(userServices.search(any()))
        .thenReturn(new SearchQueryResult<>(1, false, List.of(createUserEntity()), null, null));
    new AuthenticationInterceptor(new BasicAuth(userServices, mock(PasswordEncoder.class)))
        .interceptCall(
            closeStatusCapturingServerCall,
            metadata,
            (call, headers) -> {
              // then
              assertUsername().isEqualTo(1L);
              call.close(Status.OK, headers);
              return null;
            });
  }

  @Test
  public void missingUserIsRejected() {
    // when
    final CloseStatusCapturingServerCall closeStatusCapturingServerCall =
        new CloseStatusCapturingServerCall();
    final Metadata metadata = new Metadata();
    // demo:demo
    metadata.put(Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER), "Basic ZGVtbzpkZW1v");
    final var userServices = mock(UserServices.class);
    when(userServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(userServices);
    when(userServices.search(any()))
        .thenReturn(new SearchQueryResult<>(0, false, List.of(), null, null));
    new AuthenticationInterceptor(new BasicAuth(userServices, mock(PasswordEncoder.class)))
        .interceptCall(closeStatusCapturingServerCall, metadata, failingNextHandler());

    // then
    assertThat(closeStatusCapturingServerCall.closeStatus)
        .hasValueSatisfying(
            status -> {
              assertThat(status.getCode()).isEqualTo(Status.UNAUTHENTICATED.getCode());
              assertThat(status.getDescription()).isEqualTo("Invalid credentials");
            });
  }

  @Test
  public void notValidPasswordIsRejected() {
    // when
    final CloseStatusCapturingServerCall closeStatusCapturingServerCall =
        new CloseStatusCapturingServerCall();
    final Metadata metadata = new Metadata();
    // demo:demo
    metadata.put(Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER), "Basic ZGVtbzpkZW1v");
    final var userServices = mock(UserServices.class);
    when(userServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(userServices);
    when(userServices.search(any()))
        .thenReturn(new SearchQueryResult<>(1, false, List.of(createUserEntity()), null, null));
    final var passwordEncoder = mock(PasswordEncoder.class);
    when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);
    new AuthenticationInterceptor(new BasicAuth(userServices, mock(PasswordEncoder.class)))
        .interceptCall(closeStatusCapturingServerCall, metadata, failingNextHandler());

    // then
    assertThat(closeStatusCapturingServerCall.closeStatus)
        .hasValueSatisfying(
            status -> {
              assertThat(status.getCode()).isEqualTo(Status.UNAUTHENTICATED.getCode());
              assertThat(status.getDescription()).isEqualTo("Invalid credentials");
            });
  }

  // OIDC auth tests
  @Test
  public void validTokenIsAccepted() {
    // when
    final CloseStatusCapturingServerCall closeStatusCapturingServerCall =
        new CloseStatusCapturingServerCall();

    // Create a mock JWT with claims
    final var jwt = mock(org.springframework.security.oauth2.jwt.Jwt.class);
    final Map<String, Object> claims =
        Map.of(
            "role", "admin",
            "foo", "bar",
            "baz", "qux",
            "username", "test-user");
    when(jwt.getClaims()).thenReturn(claims);

    // Configure the JwtDecoder to return our mock JWT
    final var jwtDecoder = mock(JwtDecoder.class);
    when(jwtDecoder.decode(anyString())).thenReturn(jwt);

    final var oidcAuthenticationConfiguration = new OidcAuthenticationConfiguration();
    oidcAuthenticationConfiguration.setUsernameClaim("username");
    oidcAuthenticationConfiguration.setClientIdClaim("application_id");

    new AuthenticationInterceptor(new Oidc(jwtDecoder, oidcAuthenticationConfiguration))
        .interceptCall(
            closeStatusCapturingServerCall,
            createAuthHeader(),
            (call, headers) -> {
              call.close(Status.OK, headers);
              return null;
            });

    // then
    assertThat(closeStatusCapturingServerCall.closeStatus).hasValue(Status.OK);
  }

  @Test
  public void missingUsernameAndClientIdIsRejected() {
    // when
    final CloseStatusCapturingServerCall closeStatusCapturingServerCall =
        new CloseStatusCapturingServerCall();

    final var jwt = mock(org.springframework.security.oauth2.jwt.Jwt.class);
    final Map<String, Object> claims = Map.of("username", "test-user");
    when(jwt.getClaims()).thenReturn(claims);

    final var jwtDecoder = mock(JwtDecoder.class);
    when(jwtDecoder.decode(anyString())).thenReturn(jwt);

    final var oidcAuthenticationConfiguration = new OidcAuthenticationConfiguration();
    oidcAuthenticationConfiguration.setUsernameClaim("sub");
    oidcAuthenticationConfiguration.setClientIdClaim("client_id");

    new AuthenticationInterceptor(new Oidc(jwtDecoder, oidcAuthenticationConfiguration))
        .interceptCall(
            closeStatusCapturingServerCall,
            createAuthHeader(),
            (call, headers) -> {
              call.close(Status.OK, headers);
              return null;
            });

    assertThat(closeStatusCapturingServerCall.closeStatus)
        .hasValueSatisfying(
            status -> {
              assertThat(status.getCode()).isEqualTo(Status.UNAUTHENTICATED.getCode());
              assertThat(status.getDescription())
                  .isEqualTo(
                      "Expected either a username (claim: sub) or client ID (claim: client_id) on the token, but no matching claim found");
            });
  }

  @Test
  public void nonStringUsernameIsRejected() {
    // when
    final CloseStatusCapturingServerCall closeStatusCapturingServerCall =
        new CloseStatusCapturingServerCall();

    final var jwt = mock(org.springframework.security.oauth2.jwt.Jwt.class);
    final Map<String, Object> claims = Map.of("username", List.of("test-user"));
    when(jwt.getClaims()).thenReturn(claims);

    final var jwtDecoder = mock(JwtDecoder.class);
    when(jwtDecoder.decode(anyString())).thenReturn(jwt);

    final var oidcAuthenticationConfiguration = new OidcAuthenticationConfiguration();
    oidcAuthenticationConfiguration.setUsernameClaim("username");
    oidcAuthenticationConfiguration.setClientIdClaim("application_id");

    new AuthenticationInterceptor(new Oidc(jwtDecoder, oidcAuthenticationConfiguration))
        .interceptCall(
            closeStatusCapturingServerCall,
            createAuthHeader(),
            (call, headers) -> {
              call.close(Status.OK, headers);
              return null;
            });

    assertThat(closeStatusCapturingServerCall.closeStatus)
        .hasValueSatisfying(
            status -> {
              assertThat(status.getCode()).isEqualTo(Status.UNAUTHENTICATED.getCode());
              assertThat(status.getDescription())
                  .isEqualTo("Failed to load OIDC principals, see cause for details");
              assertThat(status.getCause())
                  .isInstanceOf(IllegalArgumentException.class)
                  .hasMessageContaining(
                      "Value for $['username'] is not a string. Please check your OIDC configuration.");
            });
  }

  @Test
  public void nonStringClientIdIsRejected() {
    // when
    final CloseStatusCapturingServerCall closeStatusCapturingServerCall =
        new CloseStatusCapturingServerCall();

    final var jwt = mock(org.springframework.security.oauth2.jwt.Jwt.class);
    final Map<String, Object> claims = Map.of("client_id", List.of("test-user"));
    when(jwt.getClaims()).thenReturn(claims);

    final var jwtDecoder = mock(JwtDecoder.class);
    when(jwtDecoder.decode(anyString())).thenReturn(jwt);

    final var oidcAuthenticationConfiguration = new OidcAuthenticationConfiguration();
    oidcAuthenticationConfiguration.setUsernameClaim("username");
    oidcAuthenticationConfiguration.setClientIdClaim("client_id");

    new AuthenticationInterceptor(new Oidc(jwtDecoder, oidcAuthenticationConfiguration))
        .interceptCall(
            closeStatusCapturingServerCall,
            createAuthHeader(),
            (call, headers) -> {
              call.close(Status.OK, headers);
              return null;
            });

    assertThat(closeStatusCapturingServerCall.closeStatus)
        .hasValueSatisfying(
            status -> {
              assertThat(status.getCode()).isEqualTo(Status.UNAUTHENTICATED.getCode());
              assertThat(status.getDescription())
                  .isEqualTo("Failed to load OIDC principals, see cause for details");
              assertThat(status.getCause())
                  .isInstanceOf(IllegalArgumentException.class)
                  .hasMessageContaining(
                      "Value for $['client_id'] is not a string. Please check your OIDC configuration.");
            });
  }

  @Test
  public void addsUserClaimsToContext() {
    // given
    final Metadata metadata = createAuthHeader();
    final CloseStatusCapturingServerCall closeStatusCapturingServerCall =
        new CloseStatusCapturingServerCall();

    // Create a mock JWT with claims
    final var jwt = mock(org.springframework.security.oauth2.jwt.Jwt.class);
    final Map<String, Object> claims =
        Map.of(
            "role", "admin",
            "foo", "bar",
            "baz", "qux",
            "username", "test-user");
    when(jwt.getClaims()).thenReturn(claims);

    // Configure the JwtDecoder to return our mock JWT
    final var jwtDecoder = mock(JwtDecoder.class);
    when(jwtDecoder.decode(anyString())).thenReturn(jwt);

    final var oidcAuthenticationConfiguration = new OidcAuthenticationConfiguration();
    oidcAuthenticationConfiguration.setUsernameClaim("username");
    oidcAuthenticationConfiguration.setClientIdClaim("application_id");

    // when
    new AuthenticationInterceptor(new Oidc(jwtDecoder, oidcAuthenticationConfiguration))
        .interceptCall(
            closeStatusCapturingServerCall,
            metadata,
            (call, headers) -> {
              // then
              assertUserClaims().containsKey("role").containsKey("foo").containsKey("baz");
              assertUsername().isEqualTo("test-user");
              call.close(Status.OK, headers);
              return null;
            });
  }

  @Test
  public void addsUsernameInOidcToContext() {
    // given
    final Metadata metadata = createAuthHeader();
    final CloseStatusCapturingServerCall closeStatusCapturingServerCall =
        new CloseStatusCapturingServerCall();

    // Create a mock JWT with claims
    final var jwt = mock(org.springframework.security.oauth2.jwt.Jwt.class);
    final Map<String, Object> claims = Map.of("username", "test-user");
    when(jwt.getClaims()).thenReturn(claims);

    // Configure the JwtDecoder to return our mock JWT
    final var jwtDecoder = mock(JwtDecoder.class);
    when(jwtDecoder.decode(anyString())).thenReturn(jwt);

    final var oidcAuthenticationConfiguration = new OidcAuthenticationConfiguration();
    oidcAuthenticationConfiguration.setUsernameClaim("username");
    oidcAuthenticationConfiguration.setClientIdClaim("application_id");

    // when
    new AuthenticationInterceptor(new Oidc(jwtDecoder, oidcAuthenticationConfiguration))
        .interceptCall(
            closeStatusCapturingServerCall,
            metadata,
            (call, headers) -> {
              // then
              assertUsername().isEqualTo("test-user");
              call.close(Status.OK, headers);
              return null;
            });
  }

  @Test
  public void addsClientIdInOidcToContext() {
    // given
    final Metadata metadata = createAuthHeader();
    final CloseStatusCapturingServerCall closeStatusCapturingServerCall =
        new CloseStatusCapturingServerCall();

    // Create a mock JWT with claims
    final var jwt = mock(org.springframework.security.oauth2.jwt.Jwt.class);
    final Map<String, Object> claims = Map.of("untested", "test-user", "application_id", "app-id");
    when(jwt.getClaims()).thenReturn(claims);

    // Configure the JwtDecoder to return our mock JWT
    final var jwtDecoder = mock(JwtDecoder.class);
    when(jwtDecoder.decode(anyString())).thenReturn(jwt);

    final var oidcAuthenticationConfiguration = new OidcAuthenticationConfiguration();
    oidcAuthenticationConfiguration.setUsernameClaim("username");
    oidcAuthenticationConfiguration.setClientIdClaim("application_id");

    // when
    new AuthenticationInterceptor(new Oidc(jwtDecoder, oidcAuthenticationConfiguration))
        .interceptCall(
            closeStatusCapturingServerCall,
            metadata,
            (call, headers) -> {
              // then;
              assertClientId().isEqualTo("app-id");

              call.close(Status.OK, headers);
              return null;
            });
  }

  @Test
  public void
      addsUsernameToOidcContextWhenPreferUsernameClaimIsTrueWhenUsernameAndClientIdClaimsArePresent() {
    // given
    final Metadata metadata = createAuthHeader();
    final CloseStatusCapturingServerCall closeStatusCapturingServerCall =
        new CloseStatusCapturingServerCall();

    // Create a mock JWT with claims
    final var jwt = mock(org.springframework.security.oauth2.jwt.Jwt.class);
    final Map<String, Object> claims = Map.of("username", "test-user", "application_id", "app-id");
    when(jwt.getClaims()).thenReturn(claims);

    // Configure the JwtDecoder to return our mock JWT
    final var jwtDecoder = mock(JwtDecoder.class);
    when(jwtDecoder.decode(anyString())).thenReturn(jwt);

    final var oidcAuthenticationConfiguration = new OidcAuthenticationConfiguration();
    oidcAuthenticationConfiguration.setUsernameClaim("username");
    oidcAuthenticationConfiguration.setClientIdClaim("application_id");
    oidcAuthenticationConfiguration.setPreferUsernameClaim(true);

    // when
    new AuthenticationInterceptor(new Oidc(jwtDecoder, oidcAuthenticationConfiguration))
        .interceptCall(
            closeStatusCapturingServerCall,
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
  public void defaultsToUsernameWhenPreferUsernameClaimIsFalseAndClientIdIsNotPresent() {
    // given
    final Metadata metadata = createAuthHeader();
    final CloseStatusCapturingServerCall closeStatusCapturingServerCall =
        new CloseStatusCapturingServerCall();

    // Create a mock JWT with claims
    final var jwt = mock(org.springframework.security.oauth2.jwt.Jwt.class);
    final Map<String, Object> claims = Map.of("username", "test-user", "application_id", "app-id");
    when(jwt.getClaims()).thenReturn(claims);

    // Configure the JwtDecoder to return our mock JWT
    final var jwtDecoder = mock(JwtDecoder.class);
    when(jwtDecoder.decode(anyString())).thenReturn(jwt);

    final var oidcAuthenticationConfiguration = new OidcAuthenticationConfiguration();
    oidcAuthenticationConfiguration.setUsernameClaim("username");
    oidcAuthenticationConfiguration.setClientIdClaim("missing_claim");

    // when
    new AuthenticationInterceptor(new Oidc(jwtDecoder, oidcAuthenticationConfiguration))
        .interceptCall(
            closeStatusCapturingServerCall,
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
  public void
      addsClientIdToOidcContextWhenPreferUsernameClaimIsFalseWhenUsernameAndClientIdClaimsArePresent() {
    // given
    final Metadata metadata = createAuthHeader();
    final CloseStatusCapturingServerCall closeStatusCapturingServerCall =
        new CloseStatusCapturingServerCall();

    // Create a mock JWT with claims
    final var jwt = mock(org.springframework.security.oauth2.jwt.Jwt.class);
    final Map<String, Object> claims = Map.of("username", "test-user", "application_id", "app-id");
    when(jwt.getClaims()).thenReturn(claims);

    // Configure the JwtDecoder to return our mock JWT
    final var jwtDecoder = mock(JwtDecoder.class);
    when(jwtDecoder.decode(anyString())).thenReturn(jwt);

    final var oidcAuthenticationConfiguration = new OidcAuthenticationConfiguration();
    oidcAuthenticationConfiguration.setUsernameClaim("username");
    oidcAuthenticationConfiguration.setClientIdClaim("application_id");

    // when
    new AuthenticationInterceptor(new Oidc(jwtDecoder, oidcAuthenticationConfiguration))
        .interceptCall(
            closeStatusCapturingServerCall,
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
  public void addsGroupsInOidcToContext() {
    // given
    final Metadata metadata = createAuthHeader();
    final CloseStatusCapturingServerCall closeStatusCapturingServerCall =
        new CloseStatusCapturingServerCall();

    // Create a mock JWT with claims
    final var jwt = mock(org.springframework.security.oauth2.jwt.Jwt.class);
    final Map<String, Object> claims =
        Map.of("username", "test-user", "groups", List.of("foo", "bar"));
    when(jwt.getClaims()).thenReturn(claims);

    // Configure the JwtDecoder to return our mock JWT
    final var jwtDecoder = mock(JwtDecoder.class);
    when(jwtDecoder.decode(anyString())).thenReturn(jwt);

    final var oidcAuthenticationConfiguration = new OidcAuthenticationConfiguration();
    oidcAuthenticationConfiguration.setUsernameClaim("username");
    oidcAuthenticationConfiguration.setClientIdClaim("application_id");
    oidcAuthenticationConfiguration.setGroupsClaim("$.groups[*]");

    // when
    new AuthenticationInterceptor(new Oidc(jwtDecoder, oidcAuthenticationConfiguration))
        .interceptCall(
            closeStatusCapturingServerCall,
            metadata,
            (call, headers) -> {
              // then
              assertGroups().containsExactlyInAnyOrder("foo", "bar");
              call.close(Status.OK, headers);
              return null;
            });
  }

  @Test
  public void nonStringArrayGroupsIsRejected() {
    // when
    final CloseStatusCapturingServerCall closeStatusCapturingServerCall =
        new CloseStatusCapturingServerCall();

    final var jwt = mock(org.springframework.security.oauth2.jwt.Jwt.class);
    final Map<String, Object> claims =
        Map.of("username", "test-user", "groups", List.of(Map.of("foo", "bar")));

    when(jwt.getClaims()).thenReturn(claims);

    final var jwtDecoder = mock(JwtDecoder.class);
    when(jwtDecoder.decode(anyString())).thenReturn(jwt);

    final var oidcAuthenticationConfiguration = new OidcAuthenticationConfiguration();
    oidcAuthenticationConfiguration.setUsernameClaim("username");
    oidcAuthenticationConfiguration.setClientIdClaim("client_id");
    oidcAuthenticationConfiguration.setGroupsClaim("$.groups[*]");

    new AuthenticationInterceptor(new Oidc(jwtDecoder, oidcAuthenticationConfiguration))
        .interceptCall(
            closeStatusCapturingServerCall,
            createAuthHeader(),
            (call, headers) -> {
              call.close(Status.OK, headers);
              return null;
            });

    assertThat(closeStatusCapturingServerCall.closeStatus)
        .hasValueSatisfying(
            status -> {
              assertThat(status.getCode()).isEqualTo(Status.UNAUTHENTICATED.getCode());
              assertThat(status.getDescription())
                  .isEqualTo("Failed to load OIDC groups, see cause for details");
              assertThat(status.getCause())
                  .isInstanceOf(IllegalArgumentException.class)
                  .hasMessageContaining(
                      "Group's list derived from ($.groups[*]) is not a string array. Please check your OIDC configuration.");
            });
  }

  @Test
  void shouldMeasureLatency() {
    // given
    final var meterRegistry = new SimpleMeterRegistry();
    final var metrics = new AuthenticationMetrics(meterRegistry, AuthenticationMethod.BASIC);
    final var capturingCall = new CloseStatusCapturingServerCall();
    final Metadata metadata = new Metadata();
    // demo:demo
    metadata.put(Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER), "Basic ZGVtbzpkZW1v");
    final var userServices = mock(UserServices.class);
    when(userServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(userServices);
    when(userServices.search(any()))
        .thenReturn(new SearchQueryResult<>(1, false, List.of(createUserEntity()), null, null));
    final var passwordEncoder = mock(PasswordEncoder.class);
    when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
    new AuthenticationInterceptor(new BasicAuth(userServices, passwordEncoder), metrics)
        .interceptCall(
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
    assertThat(capturingCall.closeStatus).hasValue(Status.OK);
  }

  @Test
  void shouldMeasureFailureLatency() {
    // given
    final var meterRegistry = new SimpleMeterRegistry();
    final var metrics = new AuthenticationMetrics(meterRegistry, AuthenticationMethod.BASIC);
    final var capturingCall = new CloseStatusCapturingServerCall();
    final Metadata metadata = new Metadata();
    // not demo:demo
    metadata.put(Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER), "Basic ZGVtbzpkZW1b");
    final var userServices = mock(UserServices.class);
    when(userServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(userServices);
    when(userServices.search(any()))
        .thenReturn(new SearchQueryResult<>(1, false, List.of(createUserEntity()), null, null));
    final var passwordEncoder = mock(PasswordEncoder.class);
    when(passwordEncoder.matches("demo", "demo")).thenReturn(true);
    new AuthenticationInterceptor(new BasicAuth(userServices, passwordEncoder), metrics)
        .interceptCall(
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

  @Test
  void shouldMeasureFailureLatencyWhenNoAuthGiven() {
    // given
    final var meterRegistry = new SimpleMeterRegistry();
    final var metrics = new AuthenticationMetrics(meterRegistry, AuthenticationMethod.BASIC);
    final var capturingCall = new CloseStatusCapturingServerCall();
    final Metadata metadata = new Metadata();
    final var userServices = mock(UserServices.class);
    when(userServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(userServices);
    when(userServices.search(any()))
        .thenReturn(new SearchQueryResult<>(1, false, List.of(createUserEntity()), null, null));
    final var passwordEncoder = mock(PasswordEncoder.class);
    when(passwordEncoder.matches("demo", "demo")).thenReturn(true);
    new AuthenticationInterceptor(new BasicAuth(userServices, passwordEncoder), metrics)
        .interceptCall(
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
        .withExpiresAt(new Date(System.currentTimeMillis() + 60 * 60 * 1000)) // Expires in 1 hour
        .sign(algorithm);
  }

  private static MapAssert<String, Object> assertUserClaims() {
    try {
      return assertThat(Context.current().call(() -> Oidc.USER_CLAIMS.get()));
    } catch (final Exception e) {
      throw new RuntimeException("Unable to retrieve user claims from context", e);
    }
  }

  private static ListAssert<String> assertGroups() {
    try {
      return assertThat(Context.current().call(() -> GROUPS_CLAIMS.get()));
    } catch (final Exception e) {
      throw new RuntimeException("Unable to retrieve user key from context", e);
    }
  }

  private static StringAssert assertUsername() {
    try {
      return (StringAssert) assertThat(Context.current().call(() -> USERNAME.get()));
    } catch (final Exception e) {
      throw new RuntimeException("Unable to retrieve user key from context", e);
    }
  }

  private static StringAssert assertClientId() {
    try {
      return (StringAssert) assertThat(Context.current().call(() -> CLIENT_ID.get()));
    } catch (final Exception e) {
      throw new RuntimeException("Unable to retrieve user key from context", e);
    }
  }

  private ServerCallHandler<Object, Object> failingNextHandler() {
    return (call, headers) -> {
      throw new RuntimeException("Should not be invoked");
    };
  }

  private UserEntity createUserEntity() {
    return new UserEntity(1L, "demo", "name", "email", "demo");
  }

  private static final class CloseStatusCapturingServerCall extends NoopServerCall<Object, Object> {
    private final AtomicReference<Status> closeStatus = new AtomicReference<>();

    @Override
    public void close(final Status status, final Metadata trailers) {
      closeStatus.set(status);
    }

    @Override
    public MethodDescriptor<Object, Object> getMethodDescriptor() {
      return mock(MethodDescriptor.class);
    }
  }
}
