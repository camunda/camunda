/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.camunda.optimize.AbstractIT;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;

import static org.camunda.optimize.service.security.AuthCookieService.AUTH_COOKIE_TOKEN_VALUE_PREFIX;
import static org.camunda.optimize.service.security.AuthCookieService.OPTIMIZE_AUTHORIZATION;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class AuthenticationIT extends AbstractIT {

  @Test
  public void authenticateUser() {
    //given
    engineIntegrationExtension.addUser("kermit", "kermit");
    engineIntegrationExtension.grantUserOptimizeAccess("kermit");

    //when
    Response response = embeddedOptimizeExtension.authenticateUserRequest("kermit", "kermit");

    //then
    assertThat(response.getStatus(), is(200));
  }

  @Test
  public void rejectLockedUser() {
    //given
    engineIntegrationExtension.addUser("kermit", "kermit");
    engineIntegrationExtension.grantUserOptimizeAccess("kermit");

    //when
    embeddedOptimizeExtension.authenticateUserRequest("kermit", "wrongPassword");
    Response response = embeddedOptimizeExtension.authenticateUserRequest("kermit", "kermit");

    //then
    assertThat(response.getStatus(),is(401));
  }

  @Test
  public void rejectWrongPassword() {
    //given
    engineIntegrationExtension.addUser("kermit", "kermit");
    engineIntegrationExtension.grantUserOptimizeAccess("kermit");

    //when
    Response response = embeddedOptimizeExtension.authenticateUserRequest("kermit", "wrong");

    //then
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void rejectUnknownUser() {
    //when
    Response response = embeddedOptimizeExtension.authenticateUserRequest("kermit", "kermit");

    //then
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void rejectOnMissingApplicationAuthorization() {
    // when
    engineIntegrationExtension.addUser("kermit", "kermit");

    // then
    Response response = embeddedOptimizeExtension.authenticateUserRequest("kermit", "kermit");
    assertThat(response.getStatus(), is(403));
  }

  @Test
  public void securingRestApiWorksWithProxy() {
    //given
    addAdminUserAndGrantAccessPermission();
    String token = authenticateAdminUser();

    //when
    Response testResponse =
      embeddedOptimizeExtension
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
      embeddedOptimizeExtension
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
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildLogOutRequest()
        .withGivenAuthToken(selfGeneratedEvilToken)
        .execute();

    assertThat(logoutResponse.getHeaders().get("Set-Cookie").get(0).toString().contains("delete cookie"), is(true));
  }

  @Test
  public void dontDeleteCookiesIfNoToken() {
    Response logoutResponse =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildLogOutRequest()
        .withoutAuthentication()
        .execute();

    assertThat(logoutResponse.getHeaders().get("Set-Cookie"), is(nullValue()));
  }

  private String authenticateAdminUser() {
    return embeddedOptimizeExtension.authenticateUser("admin", "admin");
  }

  private void addAdminUserAndGrantAccessPermission() {
    engineIntegrationExtension.addUser("admin", "admin");
    engineIntegrationExtension.grantUserOptimizeAccess("admin");
  }
}
