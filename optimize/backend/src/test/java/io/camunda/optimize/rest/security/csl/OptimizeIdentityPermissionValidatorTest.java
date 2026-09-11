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

import io.camunda.identity.sdk.authentication.exception.TokenVerificationException;
import io.camunda.optimize.rest.exceptions.NotAuthorizedException;
import io.camunda.optimize.service.security.CCSMTokenService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class OptimizeIdentityPermissionValidatorTest {

  @Mock private CCSMTokenService ccsmTokenService;

  private OptimizeIdentityPermissionValidator validator() {
    return new OptimizeIdentityPermissionValidator(ccsmTokenService);
  }

  @Test
  void shouldAcceptTokenGrantedTheOptimizePermission() {
    doNothing().when(ccsmTokenService).verifyAccessToken("token");

    assertThat(validator().validate(jwt()).hasErrors()).isFalse();
  }

  @Test
  void shouldRejectTokenLackingTheOptimizePermission() {
    doThrow(new NotAuthorizedException("User is not authorized to access Optimize"))
        .when(ccsmTokenService)
        .verifyAccessToken("token");

    assertThat(validator().validate(jwt()).hasErrors()).isTrue();
  }

  @Test
  void shouldRejectTokenIdentityCannotVerify() {
    // Covers both an invalid/expired token and Identity being unreachable: either way the
    // permission cannot be confirmed, so the token must not be accepted.
    doThrow(new TokenVerificationException("token invalid"))
        .when(ccsmTokenService)
        .verifyAccessToken("token");

    assertThat(validator().validate(jwt()).hasErrors()).isTrue();
  }

  @Test
  void shouldRejectTokenOnUnexpectedError() {
    // A security boundary must fail closed on the unexpected rather than let it propagate as an
    // unhandled exception, so even an error outside the known Identity exception hierarchy must
    // still reject the token.
    doThrow(new IllegalStateException("unexpected"))
        .when(ccsmTokenService)
        .verifyAccessToken("token");

    assertThat(validator().validate(jwt()).hasErrors()).isTrue();
  }

  private static Jwt jwt() {
    final Instant now = Instant.now();
    return Jwt.withTokenValue("token")
        .header("alg", "none")
        .issuedAt(now)
        .expiresAt(now.plusSeconds(300))
        .subject("user")
        .build();
  }
}
