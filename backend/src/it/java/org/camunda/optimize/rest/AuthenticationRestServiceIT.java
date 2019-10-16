/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.test.it.extension.ElasticSearchIntegrationTestExtensionRule;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtensionRule;
import org.camunda.optimize.test.it.extension.EngineIntegrationExtensionRule;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

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

public class AuthenticationRestServiceIT {

  @RegisterExtension
  @Order(1)
  public ElasticSearchIntegrationTestExtensionRule elasticSearchIntegrationTestExtensionRule = new ElasticSearchIntegrationTestExtensionRule();
  @RegisterExtension
  @Order(2)
  public EngineIntegrationExtensionRule engineIntegrationExtensionRule = new EngineIntegrationExtensionRule();
  @RegisterExtension
  @Order(3)
  public EmbeddedOptimizeExtensionRule embeddedOptimizeExtensionRule = new EmbeddedOptimizeExtensionRule();

  @Test
  public void authenticateUser() {
    // given
    addAdminUserAndGrantAccessPermission();

    //when
    Response response = embeddedOptimizeExtensionRule.authenticateUserRequest("admin", "admin");

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
    Response logoutResponse = embeddedOptimizeExtensionRule
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
    Response logoutResponse = embeddedOptimizeExtensionRule
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
    Response logoutResponse = embeddedOptimizeExtensionRule
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
    Response logoutResponse = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildAuthTestRequest()
      .execute();

    //then
    assertThat(logoutResponse.getStatus(), is(200));
  }

  @Test
  public void cookieIsInsecureIfHttpIsEnabled() {
    //when
    Response authResponse = embeddedOptimizeExtensionRule
      .authenticateUserRequest(DEFAULT_USERNAME, DEFAULT_PASSWORD);

    //then
    assertThat(authResponse.getCookies().get(OPTIMIZE_AUTHORIZATION).isSecure(), is(false));
  }


  @Test
  public void cookieIsSecureIfHttpIsDisabled() {
    // given
    embeddedOptimizeExtensionRule.getConfigurationService().setContainerHttpPort(Optional.empty());

    // when
    Response authResponse = embeddedOptimizeExtensionRule
      .authenticateUserRequest(DEFAULT_USERNAME, DEFAULT_PASSWORD);

    // then
    assertThat(authResponse.getCookies().get(OPTIMIZE_AUTHORIZATION).isSecure(), is(true));
  }

  @Test
  public void cookieIsHttpOnly() {
    // when
    Response authResponse = embeddedOptimizeExtensionRule
      .authenticateUserRequest(DEFAULT_USERNAME, DEFAULT_PASSWORD);

    // then
    assertThat(authResponse.getCookies().get(OPTIMIZE_AUTHORIZATION).isHttpOnly(), is(true));
  }

  @Test
  public void canDisableSameSiteCookieFlag() {
    // given
    embeddedOptimizeExtensionRule.getConfigurationService().setSameSiteCookieFlagEnabled(false);

    // when
    Response authResponse = embeddedOptimizeExtensionRule
      .authenticateUserRequest(DEFAULT_USERNAME, DEFAULT_PASSWORD);

    // then
    assertThat(
      authResponse.getHeaders().getFirst(HttpHeaders.SET_COOKIE).toString(),
      not(containsString(SAME_SITE_COOKIE_FLAG))
    );

    // cleanup
    embeddedOptimizeExtensionRule.getConfigurationService().setSameSiteCookieFlagEnabled(true);
  }

  @Test
  public void cookieHasSameSiteCookieFlagEnabledByDefault() {
    // when
    Response authResponse = embeddedOptimizeExtensionRule
      .authenticateUserRequest(DEFAULT_USERNAME, DEFAULT_PASSWORD);

    // then
    assertThat(
      authResponse.getHeaders().getFirst(HttpHeaders.SET_COOKIE).toString(),
      containsString(SAME_SITE_COOKIE_FLAG + "=" + SAME_SITE_COOKIE_STRICT_VALUE)
    );
  }

  private String authenticateAdminUser() {
    return embeddedOptimizeExtensionRule.authenticateUser("admin", "admin");
  }

  private void addAdminUserAndGrantAccessPermission() {
    engineIntegrationExtensionRule.addUser("admin", "admin");
    engineIntegrationExtensionRule.grantUserOptimizeAccess("admin");
  }

}