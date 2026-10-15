/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security;

import static io.camunda.operate.webapp.security.OperateURIs.LOGIN_RESOURCE;
import static io.camunda.operate.webapp.security.OperateURIs.LOGOUT_RESOURCE;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.property.OperateProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;

class BaseWebConfigurerTest {

  private final BaseWebConfigurer configurer =
      new BaseWebConfigurer(new OperateProperties(), null) {
        @Override
        protected void applyOAuth2Settings(final HttpSecurity http) {}
      };

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void shouldAddCsrfTokenToLoginGetWithoutAuthentication() {
    // a visitor without a session needs a token before it can POST the login form
    assertThat(configurer.shouldAddCSRF(request("GET", LOGIN_RESOURCE))).isTrue();
  }

  @Test
  void shouldAddCsrfTokenToLoginPost() {
    assertThat(configurer.shouldAddCSRF(request("POST", LOGIN_RESOURCE))).isTrue();
  }

  @Test
  void shouldAddCsrfTokenToAuthenticatedGet() {
    authenticate();
    assertThat(configurer.shouldAddCSRF(request("GET", "/api/authentications/user"))).isTrue();
  }

  @Test
  void shouldNotAddCsrfTokenToUnauthenticatedGet() {
    assertThat(configurer.shouldAddCSRF(request("GET", "/api/authentications/user"))).isFalse();
  }

  @Test
  void shouldNotAddCsrfTokenToAuthenticatedPost() {
    authenticate();
    assertThat(configurer.shouldAddCSRF(request("POST", "/api/process-instances"))).isFalse();
  }

  @Test
  void shouldNotAddCsrfTokenToLogout() {
    authenticate();
    assertThat(configurer.shouldAddCSRF(request("GET", LOGOUT_RESOURCE))).isFalse();
  }

  @Test
  void shouldNotTreatPathContainingLoginAsLogin() {
    // only the login endpoint itself gets a token without authentication
    assertThat(configurer.shouldAddCSRF(request("POST", "/api/login-history"))).isFalse();
  }

  private static MockHttpServletRequest request(final String method, final String servletPath) {
    final var request = new MockHttpServletRequest(method, servletPath);
    request.setServletPath(servletPath);
    return request;
  }

  private static void authenticate() {
    SecurityContextHolder.getContext()
        .setAuthentication(new TestingAuthenticationToken("demo", "demo", "ROLE_USER"));
  }
}
