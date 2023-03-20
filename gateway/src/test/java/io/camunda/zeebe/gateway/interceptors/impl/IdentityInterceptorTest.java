/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.interceptors.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.authentication.exception.TokenVerificationException;
import io.grpc.Metadata;
import io.grpc.Metadata.Key;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import io.grpc.internal.NoopServerCall;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public class IdentityInterceptorTest {

  @Test
  public void missingTokenIsRejected() {
    // given
    final Identity identityMock = mock(Identity.class);

    // when
    final CloseStatusCapturingServerCall closeStatusCapturingServerCall =
        new CloseStatusCapturingServerCall();
    new IdentityInterceptor(identityMock)
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
  public void invalidTokenIsRejected() {
    // given
    final Identity identityMock = mock(Identity.class, RETURNS_DEEP_STUBS);
    when(identityMock.authentication().verifyToken(anyString()))
        .thenThrow(TokenVerificationException.class);

    // when
    final CloseStatusCapturingServerCall closeStatusCapturingServerCall =
        new CloseStatusCapturingServerCall();
    new IdentityInterceptor(identityMock)
        .interceptCall(closeStatusCapturingServerCall, createAuthHeader(), failingNextHandler());

    // then
    assertThat(closeStatusCapturingServerCall.closeStatus)
        .hasValueSatisfying(
            status -> {
              assertThat(status.getCode()).isEqualTo(Status.UNAUTHENTICATED.getCode());
              assertThat(status.getDescription())
                  .isEqualTo("Failed to parse bearer token, see cause for details");
              assertThat(status.getCause()).isInstanceOf(TokenVerificationException.class);
            });
  }

  @Test
  public void genericRuntimeFailure() {
    // given
    final Identity identityMock = mock(Identity.class, RETURNS_DEEP_STUBS);
    when(identityMock.authentication().verifyToken(anyString())).thenThrow(RuntimeException.class);

    // when
    assertThatThrownBy(
            () ->
                new IdentityInterceptor(identityMock)
                    .interceptCall(
                        new NoopServerCall<>(), createAuthHeader(), failingNextHandler()))
        // then
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  public void validTokenIsAccepted() {
    // given
    final Identity identityMock = mock(Identity.class, RETURNS_DEEP_STUBS);
    when(identityMock.authentication().verifyToken(anyString())).thenReturn(null);

    // when
    final CloseStatusCapturingServerCall closeStatusCapturingServerCall =
        new CloseStatusCapturingServerCall();
    new IdentityInterceptor(identityMock)
        .interceptCall(
            closeStatusCapturingServerCall,
            createAuthHeader(),
            (call, headers) -> {
              call.close(Status.OK, headers);
              return null;
            });

    // then
    assertThat(closeStatusCapturingServerCall.closeStatus)
        .hasValueSatisfying(status -> assertThat(status.getCode()).isEqualTo(Status.OK.getCode()));
  }

  private Metadata createAuthHeader() {
    final Metadata requestMetaData = new Metadata();
    requestMetaData.put(Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER), "BAR");
    return requestMetaData;
  }

  private ServerCallHandler<Object, Object> failingNextHandler() {
    return (call, headers) -> {
      throw new RuntimeException("Should not be invoked");
    };
  }

  private static class CloseStatusCapturingServerCall extends NoopServerCall<Object, Object> {
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
