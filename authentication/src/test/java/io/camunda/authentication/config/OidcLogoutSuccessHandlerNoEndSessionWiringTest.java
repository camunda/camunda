/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.authentication.config.controllers.WebSecurityConfigTestContext;
import io.camunda.authentication.config.controllers.WebSecurityOidcTestContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Companion to {@link OidcLogoutSuccessHandlerWiringTest} covering the case where the OIDC provider
 * has no {@code end_session_endpoint} configured (no {@code
 * camunda.security.authentication.oidc.end-session-endpoint-uri} property here, unlike the sibling
 * test class). Regression test for camunda/camunda-security-library#484.
 */
@SpringBootTest(
    classes = {
      WebSecurityConfigTestContext.class,
      WebSecurityOidcTestContext.class,
      WebSecurityConfig.class
    },
    properties = {
      "camunda.security.authentication.unprotected-api=false",
      "camunda.security.authentication.method=oidc",
      "camunda.security.authentication.oidc.client-id=example",
      "camunda.security.authentication.oidc.redirect-uri=https://redirect.example.com",
      "camunda.security.authentication.oidc.authorization-uri=authorization.example.com",
      "camunda.security.authentication.oidc.token-uri=token.example.com",
      "camunda.security.authentication.oidc.jwk-set-uri=jwks.example.com"
    })
public class OidcLogoutSuccessHandlerNoEndSessionWiringTest extends AbstractWebSecurityConfigTest {

  @Test
  public void shouldReturnNoContentForFetchLogoutWhenNoEndSessionEndpointConfigured()
      throws Exception {
    // given an OIDC-authenticated fetch()/XHR caller whose registration has no
    // end_session_endpoint

    // when it POSTs /logout
    final MvcTestResult result =
        mockMvcTester
            .post()
            .uri("https://localhost/logout")
            .header("Sec-Fetch-Dest", "empty")
            .accept(MediaType.APPLICATION_JSON)
            .with(oidcLogin())
            .exchange();

    // then it gets an empty 204, not a 302 redirect to a non-existent end-session endpoint
    assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
    assertThat(result.getResponse().getContentAsString()).isEmpty();
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor oidcLogin() {
    final ClientRegistration registration =
        ClientRegistration.withRegistrationId("oidc")
            .clientId("example")
            .clientSecret("secret")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/sso-callback")
            .authorizationUri("https://authorization.example.com")
            .tokenUri("https://token.example.com")
            .jwkSetUri("https://jwks.example.com")
            .userNameAttributeName("sub")
            .scope("openid")
            .build();

    return SecurityMockMvcRequestPostProcessors.oidcLogin().clientRegistration(registration);
  }
}
