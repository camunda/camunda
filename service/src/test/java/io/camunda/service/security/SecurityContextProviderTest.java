/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.security.auth.Authentication;
import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.SecurityContext;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.impl.AuthorizationChecker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

class SecurityContextProviderTest {

  private SecurityConfiguration securityConfiguration;
  private AuthorizationChecker authorizationChecker;
  private SecurityContextProvider securityContextProvider;

  @BeforeEach
  void before() {
    securityConfiguration = mock(SecurityConfiguration.class, Answers.RETURNS_DEEP_STUBS);
    authorizationChecker = mock(AuthorizationChecker.class);
    securityContextProvider =
        new SecurityContextProvider(securityConfiguration, authorizationChecker);
  }

  @Test
  void shouldProvideSecurityContextWithAuthorizationWhenDisabled() {
    // given
    final var authentication = mock(Authentication.class);
    final var authorization = mock(Authorization.class);
    when(securityConfiguration.getAuthorizations().isEnabled()).thenReturn(true);

    // when
    final var securityContext =
        securityContextProvider.provideSecurityContext(authentication, authorization);

    // then
    assertThat(securityContext.authorization()).isEqualTo(authorization);
    assertThat(securityContext.requiresAuthorizationChecks()).isTrue();
    assertThat(securityContext.authentication()).isEqualTo(authentication);
  }

  @Test
  void shouldProvideSecurityContextWithoutAuthorizationWhenDisabled() {
    // given
    final var authentication = mock(Authentication.class);
    final var authorization = mock(Authorization.class);
    when(securityConfiguration.getAuthorizations().isEnabled()).thenReturn(false);

    // when
    final var securityContext =
        securityContextProvider.provideSecurityContext(authentication, authorization);

    // then
    assertThat(securityContext.authorization()).isNull();
    assertThat(securityContext.requiresAuthorizationChecks()).isFalse();
    assertThat(securityContext.authentication()).isEqualTo(authentication);
  }

  @Test
  void isAuthorizedShouldReturnTrueWhenAuthorizationIsDisabled() {
    // given
    final var authentication = mock(Authentication.class);
    final var authorization = mock(Authorization.class);
    when(securityConfiguration.getAuthorizations().isEnabled()).thenReturn(false);

    // when
    final var authorized =
        securityContextProvider.isAuthorized("resourceKey", authentication, authorization);

    // then
    assertThat(authorized).isTrue();
    verify(authorizationChecker, never()).isAuthorized(any(), any());
  }

  @Test
  void isAuthorizedShouldReturnTrueWhenAuthorizationCheckerReturnsTrue() {
    // given
    final var authentication = mock(Authentication.class);
    final var authorization = mock(Authorization.class);
    when(securityConfiguration.getAuthorizations().isEnabled()).thenReturn(true);
    when(authorizationChecker.isAuthorized(any(), any())).thenReturn(true);

    // when
    final var authorized =
        securityContextProvider.isAuthorized("resourceKey", authentication, authorization);

    // then
    assertThat(authorized).isTrue();
    verify(authorizationChecker)
        .isAuthorized(
            "resourceKey",
            SecurityContext.of(
                s -> s.withAuthentication(authentication).withAuthorization(authorization)));
  }

  @Test
  void isAuthorizedShouldReturnFalseWhenAuthorizationCheckerReturnsFalse() {
    // given
    final var authentication = mock(Authentication.class);
    final var authorization = mock(Authorization.class);
    when(securityConfiguration.getAuthorizations().isEnabled()).thenReturn(true);
    when(authorizationChecker.isAuthorized(any(), any())).thenReturn(false);

    // when
    final var authorized =
        securityContextProvider.isAuthorized("resourceKey", authentication, authorization);

    // then
    assertThat(authorized).isFalse();
    verify(authorizationChecker)
        .isAuthorized(
            "resourceKey",
            SecurityContext.of(
                s -> s.withAuthentication(authentication).withAuthorization(authorization)));
  }
}
