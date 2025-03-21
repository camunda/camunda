/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.interceptors.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import io.camunda.search.entities.UserEntity;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.service.UserServices;
import io.camunda.zeebe.gateway.interceptors.impl.AuthenticationHandler.BasicAuth;
import io.camunda.zeebe.gateway.interceptors.impl.AuthenticationHandler.Oidc;
import io.grpc.Context;
import io.grpc.Metadata;
import io.grpc.Metadata.Key;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
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
    when(userServices.search(any()))
        .thenReturn(new SearchQueryResult<>(1, List.of(createUserEntity()), null, null));
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
        .thenReturn(new SearchQueryResult<>(1, List.of(createUserEntity()), null, null));
    new AuthenticationInterceptor(new BasicAuth(userServices, mock(PasswordEncoder.class)))
        .interceptCall(
            closeStatusCapturingServerCall,
            metadata,
            (call, headers) -> {
              // then
              assertUserKey().isEqualTo(1L);
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
    when(userServices.search(any())).thenReturn(new SearchQueryResult<>(0, List.of(), null, null));
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
    when(userServices.search(any()))
        .thenReturn(new SearchQueryResult<>(1, List.of(createUserEntity()), null, null));
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
            "baz", "qux");
    when(jwt.getClaims()).thenReturn(claims);

    // Configure the JwtDecoder to return our mock JWT
    final var jwtDecoder = mock(JwtDecoder.class);
    when(jwtDecoder.decode(anyString())).thenReturn(jwt);

    new AuthenticationInterceptor(new Oidc(jwtDecoder))
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
            "baz", "qux");
    when(jwt.getClaims()).thenReturn(claims);

    // Configure the JwtDecoder to return our mock JWT
    final var jwtDecoder = mock(JwtDecoder.class);
    when(jwtDecoder.decode(anyString())).thenReturn(jwt);

    // when
    new AuthenticationInterceptor(new Oidc(jwtDecoder))
        .interceptCall(
            closeStatusCapturingServerCall,
            metadata,
            (call, headers) -> {
              // then
              assertUserClaims().containsKey("role").containsKey("foo").containsKey("baz");
              call.close(Status.OK, headers);
              return null;
            });
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

  private static StringAssert assertUserKey() {
    try {
      return (StringAssert) assertThat(Context.current().call(() -> BasicAuth.USERNAME.get()));
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
