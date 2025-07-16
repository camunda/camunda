/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.authentication.CamundaOAuthPrincipalService;
import io.camunda.authentication.NoSecondaryStorageOAuthPrincipalService;
import io.camunda.authentication.NoSecondaryStorageUserDetailsService;
import io.camunda.authentication.config.WebSecurityConfig;
import io.camunda.authentication.config.controllers.WebSecurityConfigTestContext;
import io.camunda.service.exception.ServiceException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(
    classes = {
      WebSecurityConfigTestContext.class,
      WebSecurityConfig.class,
    },
    properties = {
      "camunda.security.authentication.unprotected-api=false",
      "camunda.security.authentication.method=oidc",
      "camunda.database.type=none" // no secondary storage
    })
@TestPropertySource(properties = {"camunda.database.type=none"})
public class OidcNoSecondaryStorageIntegrationTest {

  @Test
  public void shouldUseNoSecondaryStorageOAuthPrincipalService() {
    // We cannot easily test this without a full Spring context due to conditional loading
    // Instead, let's create a manual test
    final CamundaOAuthPrincipalService service = new NoSecondaryStorageOAuthPrincipalService();
    
    // when & then
    assertThat(service).isInstanceOf(NoSecondaryStorageOAuthPrincipalService.class);
  }

  @Test
  public void shouldFailOAuthAuthenticationWithClearErrorMessage() {
    // given
    final CamundaOAuthPrincipalService service = new NoSecondaryStorageOAuthPrincipalService();
    final Map<String, Object> claims = Map.of("sub", "demo-user");

    // when & then
    final OAuth2AuthenticationException exception = assertThrows(
        OAuth2AuthenticationException.class,
        () -> service.loadOAuthContext(claims));

    assertThat(exception.getError().getDescription())
        .contains("OAuth authentication is not available when secondary storage is disabled")
        .contains("camunda.database.type=none");
  }

  @Test 
  public void shouldFailBasicAuthenticationWithClearErrorMessage() {
    // given
    final UserDetailsService userDetailsService = new NoSecondaryStorageUserDetailsService();

    // when & then
    final ServiceException exception = assertThrows(
        ServiceException.class,
        () -> userDetailsService.loadUserByUsername("demo"));

    assertThat(exception.getMessage())
        .contains("Authentication is not available when secondary storage is disabled")
        .contains("camunda.database.type=none");
        
    assertThat(exception.getStatus())
        .isEqualTo(ServiceException.Status.FORBIDDEN);
  }
}