/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.interceptors.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import io.camunda.zeebe.gateway.interceptors.InterceptorUtil;
import io.grpc.Context;
import io.grpc.Metadata;
import io.grpc.Metadata.Key;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.MapAssert;
import org.junit.jupiter.api.Test;

public class AuthenticationInterceptorTest {

  @Test
  public void missingTokenIsRejected() {
    // when
    final CloseStatusCapturingServerCall closeStatusCapturingServerCall =
        new CloseStatusCapturingServerCall();
    new AuthenticationInterceptor()
        .interceptCall(closeStatusCapturingServerCall, new Metadata(), failingNextHandler());

    // then
    assertThat(closeStatusCapturingServerCall.closeStatus)
        .hasValueSatisfying(
            status -> {
              assertThat(status.getCode()).isEqualTo(Status.UNAUTHENTICATED.getCode());
              assertThat(status.getDescription())
                  .isEqualTo(
                      "Expected bearer token at header with key [authorization], but found nothing");
            });
  }

  @Test
  public void validTokenIsAccepted() {
    // when
    final CloseStatusCapturingServerCall closeStatusCapturingServerCall =
        new CloseStatusCapturingServerCall();
    new AuthenticationInterceptor()
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

    // when
    new AuthenticationInterceptor()
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
    metadata.put(Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER), generateToken());
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
      return assertThat(Context.current().call(() -> InterceptorUtil.getUserClaimsKey().get()));
    } catch (final Exception e) {
      throw new RuntimeException("Unable to retrieve user claims from context", e);
    }
  }

  private ServerCallHandler<Object, Object> failingNextHandler() {
    return (call, headers) -> {
      throw new RuntimeException("Should not be invoked");
    };
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
