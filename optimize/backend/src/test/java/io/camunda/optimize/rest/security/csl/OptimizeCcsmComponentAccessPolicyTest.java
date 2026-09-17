/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import io.camunda.identity.sdk.authentication.exception.TokenVerificationException;
import io.camunda.optimize.rest.exceptions.NotAuthorizedException;
import io.camunda.optimize.service.security.CCSMTokenService;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.api.model.Either;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OptimizeCcsmComponentAccessPolicyTest {

  private static final CamundaAuthentication AUTHENTICATION =
      CamundaAuthentication.of(builder -> builder.user("kermit"));

  @Mock private CCSMTokenService tokenService;

  @Test
  void shouldAllowSessionWhenTokenHoldsTheOptimizePermission() {
    // given
    when(tokenService.getCurrentUserAuthToken()).thenReturn(Optional.of("token"));
    doNothing().when(tokenService).verifyAccessToken("token");

    // when
    final Either<String, Void> access = policy().checkAccess(AUTHENTICATION);

    // then
    assertThat(access.isRight()).isTrue();
  }

  @Test
  void shouldDenySessionWhenTokenLacksTheOptimizePermission() {
    // given
    when(tokenService.getCurrentUserAuthToken()).thenReturn(Optional.of("token"));
    doThrow(new NotAuthorizedException("User is not authorized to access Optimize"))
        .when(tokenService)
        .verifyAccessToken("token");

    // when
    final Either<String, Void> access = policy().checkAccess(AUTHENTICATION);

    // then
    assertThat(access.isLeft()).isTrue();
    assertThat(access.leftValue()).isEqualTo("User is not authorized to access Optimize");
  }

  @Test
  void shouldAllowSessionWhenTheTokenCanNoLongerBeVerified() {
    // An expired token is renewed by the webapp chain and passed through by the API chain, so it
    // must not be mistaken for a missing permission.
    // given
    when(tokenService.getCurrentUserAuthToken()).thenReturn(Optional.of("expired"));
    doThrow(new TokenVerificationException("token expired"))
        .when(tokenService)
        .verifyAccessToken("expired");

    // when
    final Either<String, Void> access = policy().checkAccess(AUTHENTICATION);

    // then
    assertThat(access.isRight()).isTrue();
  }

  @Test
  void shouldAllowSessionWhenNoTokenIsAvailable() {
    // given
    when(tokenService.getCurrentUserAuthToken()).thenReturn(Optional.empty());

    // when
    final Either<String, Void> access = policy().checkAccess(AUTHENTICATION);

    // then
    assertThat(access.isRight()).isTrue();
  }

  private OptimizeCcsmComponentAccessPolicy policy() {
    return new OptimizeCcsmComponentAccessPolicy(tokenService);
  }
}
