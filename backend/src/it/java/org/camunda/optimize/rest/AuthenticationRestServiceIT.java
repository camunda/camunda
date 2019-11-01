/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.AbstractIT;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.util.Optional;

import static org.camunda.optimize.service.security.AuthCookieService.OPTIMIZE_AUTHORIZATION;
import static org.camunda.optimize.service.security.AuthCookieService.SAME_SITE_COOKIE_FLAG;
import static org.camunda.optimize.service.security.AuthCookieService.SAME_SITE_COOKIE_STRICT_VALUE;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class AuthenticationRestServiceIT extends AbstractIT {

  @Test
  public void authenticateUser() {
    // given
    addAdminUserAndGrantAccessPermission();

    //when
    Response response = embeddedOptimizeExtension.authenticateUserRequest("admin", "admin");

    //then
    assertThat(response.getStatus(), is(200));
    String responseEntity = response.readEntity(String.class);
    assertThat(responseEntity, is(notNullValue()));
  }

  @Test
  public void logout() {
    //given
    addAdminUserAndGrantAccessPermission();
    String token = authenticateAdminUser();

    //when
    Response logoutResponse = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildLogOutRequest()
      .withGivenAuthToken(token)
      .execute();

    //then
    assertThat(logoutResponse.getStatus(), is(200));
    String responseEntity = logoutResponse.readEntity(String.class);
    assertThat(responseEntity, is("OK"));
  }

  @Test
  public void logoutSecure() {

    //when
    Response logoutResponse = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildLogOutRequest()
      .withGivenAuthToken("randomToken")
      .execute();

    //then
    assertThat(logoutResponse.getStatus(), is(401));
  }

  @Test
  public void testAuthenticationIfNotAuthenticated() {
    //when
    Response logoutResponse = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildAuthTestRequest()
      .withoutAuthentication()
      .execute();

    //then
    assertThat(logoutResponse.getStatus(), is(401));
  }

  @Test
  public void testIfAuthenticated() {
    //when
    Response logoutResponse = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildAuthTestRequest()
      .execute();

    //then
    assertThat(logoutResponse.getStatus(), is(200));
  }

  @Test
  public void cookieIsInsecureIfHttpIsEnabled() {
    //when
    Response authResponse = embeddedOptimizeExtension
      .authenticateUserRequest(DEFAULT_USERNAME, DEFAULT_PASSWORD);

    //then
    assertThat(authResponse.getCookies().get(OPTIMIZE_AUTHORIZATION).isSecure(), is(false));
  }


  @Test
  public void cookieIsSecureIfHttpIsDisabled() {
    // given
    embeddedOptimizeExtension.getConfigurationService().setContainerHttpPort(Optional.empty());

    // when
    Response authResponse = embeddedOptimizeExtension
      .authenticateUserRequest(DEFAULT_USERNAME, DEFAULT_PASSWORD);

    // then
    assertThat(authResponse.getCookies().get(OPTIMIZE_AUTHORIZATION).isSecure(), is(true));
  }

  @Test
  public void cookieIsHttpOnly() {
    // when
    Response authResponse = embeddedOptimizeExtension
      .authenticateUserRequest(DEFAULT_USERNAME, DEFAULT_PASSWORD);

    // then
    assertThat(authResponse.getCookies().get(OPTIMIZE_AUTHORIZATION).isHttpOnly(), is(true));
  }

  @Test
  public void canDisableSameSiteCookieFlag() {
    // given
    embeddedOptimizeExtension.getConfigurationService().setSameSiteCookieFlagEnabled(false);

    // when
    Response authResponse = embeddedOptimizeExtension
      .authenticateUserRequest(DEFAULT_USERNAME, DEFAULT_PASSWORD);

    // then
    assertThat(
      authResponse.getHeaders().getFirst(HttpHeaders.SET_COOKIE).toString(),
      not(containsString(SAME_SITE_COOKIE_FLAG))
    );

    // cleanup
    embeddedOptimizeExtension.getConfigurationService().setSameSiteCookieFlagEnabled(true);
  }

  @Test
  public void cookieHasSameSiteCookieFlagEnabledByDefault() {
    // when
    Response authResponse = embeddedOptimizeExtension
      .authenticateUserRequest(DEFAULT_USERNAME, DEFAULT_PASSWORD);

    // then
    assertThat(
      authResponse.getHeaders().getFirst(HttpHeaders.SET_COOKIE).toString(),
      containsString(SAME_SITE_COOKIE_FLAG + "=" + SAME_SITE_COOKIE_STRICT_VALUE)
    );
  }

  private String authenticateAdminUser() {
    return embeddedOptimizeExtension.authenticateUser("admin", "admin");
  }

  private void addAdminUserAndGrantAccessPermission() {
    engineIntegrationExtension.addUser("admin", "admin");
    engineIntegrationExtension.grantUserOptimizeAccess("admin");
  }

}