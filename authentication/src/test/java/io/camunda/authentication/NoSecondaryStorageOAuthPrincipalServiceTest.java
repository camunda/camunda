/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

public class NoSecondaryStorageOAuthPrincipalServiceTest {

  @Test
  public void shouldThrowOAuth2AuthenticationExceptionWithClearMessage() {
    // given
    final NoSecondaryStorageOAuthPrincipalService service = new NoSecondaryStorageOAuthPrincipalService();
    final Map<String, Object> claims = Map.of("sub", "demo-user");

    // when & then
    final OAuth2AuthenticationException exception = assertThrows(
        OAuth2AuthenticationException.class,
        () -> service.loadOAuthContext(claims));

    assertThat(exception.getError().getDescription())
        .contains("OAuth authentication is not available when secondary storage is disabled")
        .contains("camunda.database.type=none")
        .contains("Please configure secondary storage to enable OAuth authentication");
  }

  @Test
  public void shouldThrowOAuth2AuthenticationExceptionForAnyClaims() {
    // given
    final NoSecondaryStorageOAuthPrincipalService service = new NoSecondaryStorageOAuthPrincipalService();

    // when & then
    assertThrows(
        OAuth2AuthenticationException.class,
        () -> service.loadOAuthContext(Map.of("sub", "admin")));

    assertThrows(
        OAuth2AuthenticationException.class,
        () -> service.loadOAuthContext(Map.of("sub", "user")));

    assertThrows(
        OAuth2AuthenticationException.class,
        () -> service.loadOAuthContext(Map.of()));
  }
}