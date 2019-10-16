/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.camunda.optimize.test.it.extension.ElasticSearchIntegrationTestExtensionRule;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtensionRule;
import org.camunda.optimize.test.it.extension.EngineIntegrationExtensionRule;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;

import static org.camunda.optimize.service.security.AuthCookieService.AUTH_COOKIE_TOKEN_VALUE_PREFIX;
import static org.camunda.optimize.service.security.AuthCookieService.OPTIMIZE_AUTHORIZATION;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class AuthenticationIT {

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
    //given
    engineIntegrationExtensionRule.addUser("kermit", "kermit");
    engineIntegrationExtensionRule.grantUserOptimizeAccess("kermit");

    //when
    Response response = embeddedOptimizeExtensionRule.authenticateUserRequest("kermit", "kermit");

    //then
    assertThat(response.getStatus(), is(200));
  }

  @Test
  public void rejectLockedUser() {
    //given
    engineIntegrationExtensionRule.addUser("kermit", "kermit");
    engineIntegrationExtensionRule.grantUserOptimizeAccess("kermit");

    //when
    embeddedOptimizeExtensionRule.authenticateUserRequest("kermit", "wrongPassword");
    Response response = embeddedOptimizeExtensionRule.authenticateUserRequest("kermit", "kermit");

    //then
    assertThat(response.getStatus(),is(401));
    String errorMessage = response.readEntity(String.class);
    assertThat(errorMessage, containsString("The user with id 'kermit' is locked."));
  }

  @Test
  public void rejectWrongPassword() {
    //given
    engineIntegrationExtensionRule.addUser("kermit", "kermit");
    engineIntegrationExtensionRule.grantUserOptimizeAccess("kermit");

    //when
    Response response = embeddedOptimizeExtensionRule.authenticateUserRequest("kermit", "wrong");

    //then
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void rejectUnknownUser() {
    //when
    Response response = embeddedOptimizeExtensionRule.authenticateUserRequest("kermit", "kermit");

    //then
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void rejectOnMissingApplicationAuthorization() {
    // when
    engineIntegrationExtensionRule.addUser("kermit", "kermit");

    // then
    Response response = embeddedOptimizeExtensionRule.authenticateUserRequest("kermit", "kermit");
    assertThat(response.getStatus(), is(403));
  }

  @Test
  public void securingRestApiWorksWithProxy() {
    //given
    addAdminUserAndGrantAccessPermission();
    String token = authenticateAdminUser();

    //when
    Response testResponse =
      embeddedOptimizeExtensionRule
        .getRequestExecutor()
        .buildAuthTestRequest()
        .withoutAuthentication()
        .addSingleCookie(OPTIMIZE_AUTHORIZATION, AUTH_COOKIE_TOKEN_VALUE_PREFIX + token)
        .addSingleCookie(HttpHeaders.AUTHORIZATION, "Basic ZGVtbzpkZW1v")
        .execute();

    //then
    assertThat(testResponse.getStatus(), is(200));
    String responseEntity = testResponse.readEntity(String.class);
    assertThat(responseEntity, is("OK"));
  }

  @Test
  public void cantKickOutUserByProvidingWrongToken() {
    // given
    addAdminUserAndGrantAccessPermission();
    authenticateAdminUser();
    Algorithm algorithm = Algorithm.HMAC256("secret");
    String selfGeneratedEvilToken = JWT.create()
      .withIssuer("admin")
      .sign(algorithm);

    //when
    Response logoutResponse =
      embeddedOptimizeExtensionRule
        .getRequestExecutor()
        .buildLogOutRequest()
        .withGivenAuthToken(selfGeneratedEvilToken)
        .execute();

    //then
    assertThat(logoutResponse.getStatus(), is(401));
  }

  @Test
  public void deleteCookiesIfInvalidToken() {
    addAdminUserAndGrantAccessPermission();
    authenticateAdminUser();
    Algorithm algorithm = Algorithm.HMAC256("secret");
    String selfGeneratedEvilToken = JWT.create()
      .withIssuer("admin")
      .sign(algorithm);

    //when
    Response logoutResponse =
      embeddedOptimizeExtensionRule
        .getRequestExecutor()
        .buildLogOutRequest()
        .withGivenAuthToken(selfGeneratedEvilToken)
        .execute();

    assertThat(logoutResponse.getHeaders().get("Set-Cookie").get(0).toString().contains("delete cookie"), is(true));
  }

  @Test
  public void dontDeleteCookiesIfNoToken() {
    Response logoutResponse =
      embeddedOptimizeExtensionRule
        .getRequestExecutor()
        .buildLogOutRequest()
        .withoutAuthentication()
        .execute();

    assertThat(logoutResponse.getHeaders().get("Set-Cookie"), is(nullValue()));
  }

  private String authenticateAdminUser() {
    return embeddedOptimizeExtensionRule.authenticateUser("admin", "admin");
  }

  private void addAdminUserAndGrantAccessPermission() {
    engineIntegrationExtensionRule.addUser("admin", "admin");
    engineIntegrationExtensionRule.grantUserOptimizeAccess("admin");
  }
}
