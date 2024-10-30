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
import io.camunda.identity.sdk.tenants.dto.Tenant;
import io.camunda.zeebe.gateway.impl.configuration.IdentityServiceCfg;
import io.camunda.zeebe.gateway.impl.configuration.MultiTenancyCfg;
import io.camunda.zeebe.gateway.interceptors.InterceptorUtil;
import io.grpc.Context;
import io.grpc.Metadata;
import io.grpc.Metadata.Key;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.ListAssert;
import org.junit.jupiter.api.Test;

public class IdentityInterceptorTest {

  private final MultiTenancyCfg multiTenancy = new MultiTenancyCfg();
  private final IdentityServiceCfg identityServiceCfg = new IdentityServiceCfg();

  @Test
  public void missingTokenIsRejected() {
    // given
    final Identity identityMock = mock(Identity.class);

    // when
    final CloseStatusCapturingServerCall closeStatusCapturingServerCall =
        new CloseStatusCapturingServerCall();
    new IdentityInterceptor(identityMock, multiTenancy, identityServiceCfg)
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
    new IdentityInterceptor(identityMock, multiTenancy, identityServiceCfg)
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
                new IdentityInterceptor(identityMock, multiTenancy, identityServiceCfg)
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
    new IdentityInterceptor(identityMock, multiTenancy, identityServiceCfg)
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
  public void addsAuthorizedTenantsToContext() {
    // given
    final Identity identity = mock(Identity.class, RETURNS_DEEP_STUBS);
    when(identity.tenants().forToken(anyString()))
        .thenReturn(List.of(new Tenant("tenant-a", "A"), new Tenant("tenant-b", "B")))
        .thenReturn(List.of(new Tenant("tenant-c", "C")));
    final var capturingServerCall = new CloseStatusCapturingServerCall();

    // when
    final var interceptor =
        new IdentityInterceptor(identity, multiTenancy.setEnabled(true), identityServiceCfg);
    interceptor.interceptCall(
        capturingServerCall,
        createAuthHeader(),
        (call, headers) -> {
          // then
          assertAuthorizedTenants()
              .describedAs("Expect that the authorized tenants is stored in the current Context")
              .contains("tenant-a", "tenant-b");
          call.close(Status.OK, headers);
          return null;
        });

    // when a second call is intercepted, the authorized tenants should be updated
    interceptor.interceptCall(
        capturingServerCall,
        createAuthHeader(),
        (call, headers) -> {
          // then
          assertAuthorizedTenants()
              .describedAs(
                  "Expect that the authorized tenants is different in another request's Context")
              .contains("tenant-c");
          call.close(Status.OK, headers);
          return null;
        });

    // then
    assertThat(capturingServerCall.closeStatus).hasValue(Status.OK);
    assertAuthorizedTenants()
        .describedAs("Expect that the authorized tenants is not available outside of a call")
        .isNull();
  }

  @Test
  public void doesNotAddAuthorizedTenantsToContextWhenMultiTenancyDisabled() {
    // given
    final Identity identity = mock(Identity.class, RETURNS_DEEP_STUBS);
    final var capturingServerCall = new CloseStatusCapturingServerCall();

    // when
    final var interceptor =
        new IdentityInterceptor(identity, multiTenancy.setEnabled(false), identityServiceCfg);
    interceptor.interceptCall(
        capturingServerCall,
        createAuthHeader(),
        (call, headers) -> {
          // then
          assertAuthorizedTenants()
              .describedAs(
                  "Expect that the authorized tenants is not available in the current Context")
              .isNull();
          call.close(Status.OK, headers);
          return null;
        });

    // then
    assertThat(capturingServerCall.closeStatus).hasValue(Status.OK);
    assertAuthorizedTenants()
        .describedAs("Expect that the authorized tenants is not available outside of a call")
        .isNull();
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

  private static ListAssert<String> assertAuthorizedTenants() {
    try {
      return assertThat(
          Context.current().call(() -> InterceptorUtil.getAuthorizedTenantsKey().get()));
    } catch (final Exception e) {
      throw new RuntimeException("Unable to retrieve authorized tenants from context", e);
    }
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
