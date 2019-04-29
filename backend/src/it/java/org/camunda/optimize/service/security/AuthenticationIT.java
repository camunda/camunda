/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;

import static org.camunda.optimize.service.security.AuthCookieService.AUTH_COOKIE_TOKEN_VALUE_PREFIX;
import static org.camunda.optimize.service.security.AuthCookieService.OPTIMIZE_AUTHORIZATION;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;


public class AuthenticationIT {

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  @Test
  public void authenticateUser() {
    //given
    engineRule.addUser("kermit", "kermit");
    engineRule.grantUserOptimizeAccess("kermit");

    //when
    Response response = embeddedOptimizeRule.authenticateUserRequest("kermit", "kermit");

    //then
    assertThat(response.getStatus(), is(200));
  }

  @Test
  public void authenticateLockedUser() {
    //given
    engineRule.addUser("kermit", "kermit");
    engineRule.grantUserOptimizeAccess("kermit");

    //when
    embeddedOptimizeRule.authenticateUserRequest("kermit", "wrongPassword");
    Response response = embeddedOptimizeRule.authenticateUserRequest("kermit", "kermit");

    //then
    assertThat(response.getStatus(),is(401));
    String errorMessage = response.readEntity(String.class);
    assertThat(errorMessage, containsString("The user with id 'kermit' is locked."));
  }

  @Test
  public void authenticateUnknownUser() {
    //when
    Response response = embeddedOptimizeRule.authenticateUserRequest("kermit", "kermit");

    //then
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void securingRestApiWorksWithProxy() {
    //given
    addAdminUserAndGrantAccessPermission();
    String token = authenticateAdminUser();

    //when
    Response testResponse =
      embeddedOptimizeRule
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
      embeddedOptimizeRule
        .getRequestExecutor()
        .buildLogOutRequest()
        .withGivenAuthToken(selfGeneratedEvilToken)
        .execute();

    //then
    assertThat(logoutResponse.getStatus(), is(401));
  }

  @Test
  public void cantAuthenticateWithoutAnyAuthorization() {
    // when
    engineRule.addUser("kermit", "kermit");

    // then
    Response response = embeddedOptimizeRule.authenticateUserRequest("kermit", "kermit");
    assertThat(response.getStatus(), is(403));
  }

  private String authenticateAdminUser() {
    return embeddedOptimizeRule.authenticateUser("admin", "admin");
  }

  private void addAdminUserAndGrantAccessPermission() {
    engineRule.addUser("admin", "admin");
    engineRule.grantUserOptimizeAccess("admin");
  }
}
