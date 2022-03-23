/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.camunda.optimize.AbstractIT;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.rest.constants.RestConstants.AUTH_COOKIE_TOKEN_VALUE_PREFIX;
import static org.camunda.optimize.rest.constants.RestConstants.OPTIMIZE_AUTHORIZATION;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;

public class AuthenticationIT extends AbstractIT {

  @Test
  public void authenticateUser() {
    // given
    engineIntegrationExtension.addUser(KERMIT_USER, KERMIT_USER);
    engineIntegrationExtension.grantUserOptimizeAccess(KERMIT_USER);

    // when
    Response response = embeddedOptimizeExtension.authenticateUserRequest(KERMIT_USER, KERMIT_USER);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @Test
  public void authenticateUserIsByDefaultCaseSensitive() {
    // given
    engineIntegrationExtension.addUser(KERMIT_USER, KERMIT_USER);
    engineIntegrationExtension.grantUserOptimizeAccess(KERMIT_USER);

    // when
    Response response = embeddedOptimizeExtension.authenticateUserRequest(
      StringUtils.swapCase(KERMIT_USER),
      KERMIT_USER
    );

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  @SneakyThrows
  public void authenticateUserWithCaseInsensitiveAuthenticationBackend() {
    // given
    final String actualUserId = KERMIT_USER;
    final String allUpperCaseUserId = actualUserId.toUpperCase();
    engineIntegrationExtension.addUser(actualUserId, actualUserId);
    engineIntegrationExtension.grantUserOptimizeAccess(actualUserId);

    final ClientAndServer engineMockServer = useAndGetEngineMockServer();

    final List<HttpRequest> mockedRequests = CaseInsensitiveAuthenticationMockUtil.setupCaseInsensitiveAuthentication(
      embeddedOptimizeExtension, engineIntegrationExtension, engineMockServer,
      allUpperCaseUserId, actualUserId
    );

    // when
    Response response = embeddedOptimizeExtension.authenticateUserRequest(allUpperCaseUserId, actualUserId);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    final String authenticationToken = response.readEntity(String.class);
    // here the actualUserId should be present, regardless of how the user logged in
    assertThat(AuthCookieService.getTokenSubject(authenticationToken)).get().isEqualTo(actualUserId);

    mockedRequests.forEach(engineMockServer::verify);
  }

  @Test
  public void rejectLockedUser() {
    // given
    engineIntegrationExtension.addUser(KERMIT_USER, KERMIT_USER);
    engineIntegrationExtension.grantUserOptimizeAccess(KERMIT_USER);

    // when
    embeddedOptimizeExtension.authenticateUserRequest(KERMIT_USER, "wrongPassword");
    Response response = embeddedOptimizeExtension.authenticateUserRequest(KERMIT_USER, KERMIT_USER);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void rejectWrongPassword() {
    // given
    engineIntegrationExtension.addUser(KERMIT_USER, KERMIT_USER);
    engineIntegrationExtension.grantUserOptimizeAccess(KERMIT_USER);

    // when
    Response response = embeddedOptimizeExtension.authenticateUserRequest(KERMIT_USER, "wrong");

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void rejectUnknownUser() {
    // when
    Response response = embeddedOptimizeExtension.authenticateUserRequest(KERMIT_USER, KERMIT_USER);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void rejectOnMissingApplicationAuthorization() {
    // when
    engineIntegrationExtension.addUser(KERMIT_USER, KERMIT_USER);

    // then
    Response response = embeddedOptimizeExtension.authenticateUserRequest(KERMIT_USER, KERMIT_USER);
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void securingRestApiWorksWithProxy() {
    // given
    addAdminUserAndGrantAccessPermission();
    String token = authenticateAdminUser();

    // when
    Response testResponse =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildAuthTestRequest()
        .withoutAuthentication()
        .addSingleCookie(OPTIMIZE_AUTHORIZATION, AUTH_COOKIE_TOKEN_VALUE_PREFIX + token)
        .addSingleHeader(HttpHeaders.AUTHORIZATION, "Basic ZGVtbzpkZW1v")
        .execute();

    // then
    assertThat(testResponse.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    String responseEntity = testResponse.readEntity(String.class);
    assertThat(responseEntity).isEqualTo(Response.Status.OK.name());
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

    // when
    Response logoutResponse =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildLogOutRequest()
        .withGivenAuthToken(selfGeneratedEvilToken)
        .execute();

    // then
    assertThat(logoutResponse.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void deleteCookiesIfInvalidToken() {
    addAdminUserAndGrantAccessPermission();
    authenticateAdminUser();
    Algorithm algorithm = Algorithm.HMAC256("secret");
    String selfGeneratedEvilToken = JWT.create()
      .withIssuer("admin")
      .sign(algorithm);

    // when
    Response logoutResponse =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildLogOutRequest()
        .withGivenAuthToken(selfGeneratedEvilToken)
        .execute();

    assertThat(logoutResponse.getHeaders().get("Set-Cookie").get(0).toString().contains("delete cookie")).isTrue();
  }

  @Test
  public void dontDeleteCookiesIfNoToken() {
    Response logoutResponse =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildLogOutRequest()
        .withoutAuthentication()
        .execute();

    assertThat(logoutResponse.getHeaders().get("Set-Cookie")).isNull();
  }

  private String authenticateAdminUser() {
    return embeddedOptimizeExtension.authenticateUser("admin", "admin");
  }

  private void addAdminUserAndGrantAccessPermission() {
    engineIntegrationExtension.addUser("admin", "admin");
    engineIntegrationExtension.grantUserOptimizeAccess("admin");
  }
}
