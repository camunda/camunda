/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import org.camunda.optimize.AbstractIT;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.security.AuthCookieService.OPTIMIZE_AUTHORIZATION;
import static org.camunda.optimize.service.security.AuthCookieService.SAME_SITE_COOKIE_FLAG;
import static org.camunda.optimize.service.security.AuthCookieService.SAME_SITE_COOKIE_STRICT_VALUE;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;

public class SecurityCookiesInResponseIT extends AbstractIT {

  @Test
  public void cookieIsInsecureIfHttpIsEnabled() {
    //when
    Response authResponse = embeddedOptimizeExtension
      .authenticateUserRequest(DEFAULT_USERNAME, DEFAULT_PASSWORD);

    //then
    assertThat(authResponse.getCookies().get(OPTIMIZE_AUTHORIZATION).isSecure()).isFalse();
  }


  @Test
  public void cookieIsSecureIfHttpIsDisabled() {
    // given
    embeddedOptimizeExtension.getConfigurationService().setContainerHttpPort(Optional.empty());

    // when
    Response authResponse = embeddedOptimizeExtension
      .authenticateUserRequest(DEFAULT_USERNAME, DEFAULT_PASSWORD);

    // then
    assertThat(authResponse.getCookies().get(OPTIMIZE_AUTHORIZATION).isSecure()).isTrue();
  }

  @Test
  public void cookieIsHttpOnly() {
    // when
    Response authResponse = embeddedOptimizeExtension
      .authenticateUserRequest(DEFAULT_USERNAME, DEFAULT_PASSWORD);

    // then
    assertThat(authResponse.getCookies().get(OPTIMIZE_AUTHORIZATION).isHttpOnly()).isTrue();
  }

  @Test
  public void canDisableSameSiteCookieFlag() {
    // given
    embeddedOptimizeExtension.getConfigurationService().setSameSiteCookieFlagEnabled(false);

    // when
    Response authResponse = embeddedOptimizeExtension
      .authenticateUserRequest(DEFAULT_USERNAME, DEFAULT_PASSWORD);

    // then
    assertThat(authResponse.getHeaders().getFirst(HttpHeaders.SET_COOKIE).toString())
      .doesNotContain(SAME_SITE_COOKIE_FLAG);

    // cleanup
    embeddedOptimizeExtension.getConfigurationService().setSameSiteCookieFlagEnabled(true);
  }

  @Test
  public void cookieHasSameSiteCookieFlagEnabledByDefault() {
    // when
    Response authResponse = embeddedOptimizeExtension
      .authenticateUserRequest(DEFAULT_USERNAME, DEFAULT_PASSWORD);

    // then
    assertThat(authResponse.getHeaders().getFirst(HttpHeaders.SET_COOKIE).toString())
      .contains(SAME_SITE_COOKIE_FLAG + "=" + SAME_SITE_COOKIE_STRICT_VALUE);
  }


}
