/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.service.es.schema.index.TerminatedUserSessionIndex;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

import static org.camunda.optimize.rest.constants.RestConstants.AUTH_COOKIE_TOKEN_VALUE_PREFIX;
import static org.camunda.optimize.rest.constants.RestConstants.OPTIMIZE_AUTHORIZATION;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TERMINATED_USER_SESSION_INDEX_NAME;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class SessionServiceIT extends AbstractIT {

  @Test
  public void verifyTerminatedSessionCleanupIsScheduledAfterStartup() {
    assertThat(getTerminatedSessionService().isCleanupScheduled(), is(true));
  }

  @Test
  public void verifyTerminatedSessionsGetCleanedUp() {
    // given
    addAdminUserAndGrantAccessPermission();

    final String token = authenticateAdminUser();

    // when
    embeddedOptimizeExtension.getRequestExecutor().buildLogOutRequest().withGivenAuthToken(token).execute();

    LocalDateUtil.setCurrentTime(OffsetDateTime.now().plus(
      embeddedOptimizeExtension.getConfigurationService().getTokenLifeTimeMinutes(),
      ChronoUnit.MINUTES
    ));
    getTerminatedSessionService().cleanup();

    // then
    assertThat(elasticSearchIntegrationTestExtension.getDocumentCountOf(TERMINATED_USER_SESSION_INDEX_NAME), is(0));
  }

  @Test
  public void authenticatingSameUserTwiceCreatesNewIndependentSession() {
    // given
    addAdminUserAndGrantAccessPermission();

    final String firstToken = authenticateAdminUser();
    final String secondToken = authenticateAdminUser();

    // when
    final Response logoutResponse =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildLogOutRequest()
        .withGivenAuthToken(firstToken)
        .execute();
    assertThat(logoutResponse.getStatus(), is(Response.Status.OK.getStatusCode()));

    // then
    final Response getPrivateReportsResponse =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildGetAllPrivateReportsRequest()
        .withGivenAuthToken(secondToken)
        .execute();

    assertThat(getPrivateReportsResponse.getStatus(), is(Response.Status.OK.getStatusCode()));
  }

  @Test
  public void logoutCreatesTerminatedSessionEntry() {
    // given
    addAdminUserAndGrantAccessPermission();

    final String token = authenticateAdminUser();

    // when
    embeddedOptimizeExtension.getRequestExecutor().buildLogOutRequest().withGivenAuthToken(token).execute();

    // then
    assertThat(elasticSearchIntegrationTestExtension.getDocumentCountOf(TERMINATED_USER_SESSION_INDEX_NAME), is(1));
  }

  @Test
  public void logoutInvalidatesToken() {
    // given
    addAdminUserAndGrantAccessPermission();

    final String token = authenticateAdminUser();

    // when
    final Response logoutResponse =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildLogOutRequest()
        .withGivenAuthToken(token)
        .execute();
    assertThat(logoutResponse.getStatus(), is(Response.Status.OK.getStatusCode()));

    // then
    final Response getPrivateReportsResponse =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildGetAllPrivateReportsRequest()
        .withGivenAuthToken(token)
        .execute();

    assertThat(getPrivateReportsResponse.getStatus(), is(Response.Status.UNAUTHORIZED.getStatusCode()));
  }

  @Test
  public void logoutInvalidatesAllTokensOfASession() {
    // given
    int expiryMinutes = embeddedOptimizeExtension.getConfigurationService().getTokenLifeTimeMinutes();
    engineIntegrationExtension.addUser("genzo", "genzo");
    engineIntegrationExtension.grantUserOptimizeAccess("genzo");

    String firstToken = embeddedOptimizeExtension.authenticateUser("genzo", "genzo");

    // when
    // modify time to get a new token for same session
    LocalDateUtil.setCurrentTime(LocalDateUtil.getCurrentDateTime().plusMinutes(expiryMinutes * 2 / 3));
    final Response getNewAuthTokenForSameSessionResponse = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildAuthTestRequest()
      .withGivenAuthToken(firstToken)
      .execute();

    assertThat(getNewAuthTokenForSameSessionResponse.getStatus(), is(Response.Status.OK.getStatusCode()));
    LocalDateUtil.reset();

    final NewCookie newAuthCookie = getNewAuthTokenForSameSessionResponse.getCookies().get(OPTIMIZE_AUTHORIZATION);
    final String newToken = newAuthCookie.getValue().replace(AUTH_COOKIE_TOKEN_VALUE_PREFIX, "");

    final Response logoutResponse =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildLogOutRequest()
        .withGivenAuthToken(firstToken)
        .execute();
    assertThat(logoutResponse.getStatus(), is(Response.Status.OK.getStatusCode()));

    // then
    final Response getPrivateReportsResponse =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildGetAllPrivateReportsRequest()
        .withGivenAuthToken(newToken)
        .execute();

    assertThat(getPrivateReportsResponse.getStatus(), is(Response.Status.UNAUTHORIZED.getStatusCode()));
  }

  @Test
  public void tokenShouldExpireAfterConfiguredTime() {
    // given
    int expiryTime = embeddedOptimizeExtension.getConfigurationService().getTokenLifeTimeMinutes();
    engineIntegrationExtension.addUser("genzo", "genzo");
    engineIntegrationExtension.grantUserOptimizeAccess("genzo");
    String firstToken = embeddedOptimizeExtension.authenticateUser("genzo", "genzo");

    Response testAuthenticationResponse = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildAuthTestRequest()
      .withGivenAuthToken(firstToken)
      .execute();
    assertThat(testAuthenticationResponse.getStatus(), is(Response.Status.OK.getStatusCode()));

    // when
    LocalDateUtil.setCurrentTime(get1MinuteAfterExpiryTime(expiryTime));
    testAuthenticationResponse = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildAuthTestRequest()
      .withGivenAuthToken(firstToken)
      .execute();

    // then
    assertThat(testAuthenticationResponse.getStatus(), is(Response.Status.UNAUTHORIZED.getStatusCode()));
  }

  @Test
  public void authCookieIsExtendedByRequestInLastThirdOfLifeTime() {
    // given
    int expiryMinutes = embeddedOptimizeExtension.getConfigurationService().getTokenLifeTimeMinutes();
    engineIntegrationExtension.addUser("genzo", "genzo");
    engineIntegrationExtension.grantUserOptimizeAccess("genzo");
    String firstToken = embeddedOptimizeExtension.authenticateUser("genzo", "genzo");

    Response testAuthenticationResponse = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildAuthTestRequest()
      .withGivenAuthToken(firstToken)
      .execute();
    assertThat(testAuthenticationResponse.getStatus(), is(Response.Status.OK.getStatusCode()));

    // when
    final OffsetDateTime dateTimeBeforeRefresh = LocalDateUtil.getCurrentDateTime();
    LocalDateUtil.setCurrentTime(dateTimeBeforeRefresh.plusMinutes(expiryMinutes * 2 / 3));
    testAuthenticationResponse = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildAuthTestRequest()
      .withGivenAuthToken(firstToken)
      .execute();

    // then
    assertThat(testAuthenticationResponse.getStatus(), is(Response.Status.OK.getStatusCode()));
    assertThat(testAuthenticationResponse.getCookies().keySet(), hasItem(OPTIMIZE_AUTHORIZATION));
    final NewCookie newAuthCookie = testAuthenticationResponse.getCookies().get(OPTIMIZE_AUTHORIZATION);
    final String newToken = newAuthCookie.getValue().replace(AUTH_COOKIE_TOKEN_VALUE_PREFIX, "");
    assertThat(newToken, is(not(equalTo(firstToken))));
    assertThat(
      newAuthCookie.getExpiry().toInstant().truncatedTo(ChronoUnit.SECONDS),
      is(LocalDateUtil.getCurrentDateTime().plusMinutes(expiryMinutes).toInstant().truncatedTo(ChronoUnit.SECONDS))
    );

  }

  @Test
  public void tokenStillValidIfTerminatedSessionsCannotBeRead() {
    try {
      // given
      addAdminUserAndGrantAccessPermission();

      final String token = authenticateAdminUser();

      // when
      // provoke failure for terminated session check
      elasticSearchIntegrationTestExtension.deleteIndexOfMapping(new TerminatedUserSessionIndex());

      final Response getPrivateReportsResponse =
        embeddedOptimizeExtension
          .getRequestExecutor()
          .buildGetAllPrivateReportsRequest()
          .withGivenAuthToken(token)
          .execute();

      // then

      assertThat(getPrivateReportsResponse.getStatus(), is(Response.Status.OK.getStatusCode()));
    } finally {
      embeddedOptimizeExtension.getElasticSearchSchemaManager().initializeSchema(
        embeddedOptimizeExtension.getOptimizeElasticClient()
      );
    }
  }

  private OffsetDateTime get1MinuteAfterExpiryTime(int expiryTime) {
    return LocalDateUtil.getCurrentDateTime().plusMinutes(expiryTime + 1);
  }

  private String authenticateAdminUser() {
    return embeddedOptimizeExtension.authenticateUser("admin", "admin");
  }

  private void addAdminUserAndGrantAccessPermission() {
    engineIntegrationExtension.addUser("admin", "admin");
    engineIntegrationExtension.grantUserOptimizeAccess("admin");
  }

  private TerminatedSessionService getTerminatedSessionService() {
    return embeddedOptimizeExtension.getApplicationContext().getBean(TerminatedSessionService.class);
  }
}
