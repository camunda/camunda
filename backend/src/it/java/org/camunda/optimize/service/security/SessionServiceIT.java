/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import org.camunda.optimize.service.es.schema.index.TerminatedUserSessionIndex;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.test.it.extension.ElasticSearchIntegrationTestExtensionRule;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtensionRule;
import org.camunda.optimize.test.it.extension.EngineDatabaseExtensionRule;
import org.camunda.optimize.test.it.extension.EngineIntegrationExtensionRule;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

import static org.camunda.optimize.service.security.AuthCookieService.AUTH_COOKIE_TOKEN_VALUE_PREFIX;
import static org.camunda.optimize.service.security.AuthCookieService.OPTIMIZE_AUTHORIZATION;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TERMINATED_USER_SESSION_INDEX_NAME;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;

public class SessionServiceIT {

  @RegisterExtension
  @Order(1)
  public ElasticSearchIntegrationTestExtensionRule elasticSearchIntegrationTestExtensionRule = new ElasticSearchIntegrationTestExtensionRule();
  @RegisterExtension
  @Order(2)
  public EngineIntegrationExtensionRule engineIntegrationExtensionRule = new EngineIntegrationExtensionRule();
  @RegisterExtension
  @Order(3)
  public EmbeddedOptimizeExtensionRule embeddedOptimizeExtensionRule = new EmbeddedOptimizeExtensionRule();
  @RegisterExtension
  @Order(4)
  public EngineDatabaseExtensionRule engineDatabaseExtensionRule = new EngineDatabaseExtensionRule(engineIntegrationExtensionRule.getEngineName());

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
    embeddedOptimizeExtensionRule.getRequestExecutor().buildLogOutRequest().withGivenAuthToken(token).execute();

    LocalDateUtil.setCurrentTime(OffsetDateTime.now().plus(
      embeddedOptimizeExtensionRule.getConfigurationService().getTokenLifeTimeMinutes(),
      ChronoUnit.MINUTES
    ));
    getTerminatedSessionService().cleanup();

    // then
    assertThat(elasticSearchIntegrationTestExtensionRule.getDocumentCountOf(TERMINATED_USER_SESSION_INDEX_NAME), is(0));
  }

  @Test
  public void authenticatingSameUserTwiceCreatesNewIndependentSession() {
    // given
    addAdminUserAndGrantAccessPermission();

    final String firstToken = authenticateAdminUser();
    final String secondToken = authenticateAdminUser();

    // when
    final Response logoutResponse =
      embeddedOptimizeExtensionRule
        .getRequestExecutor()
        .buildLogOutRequest()
        .withGivenAuthToken(firstToken)
        .execute();
    assertThat(logoutResponse.getStatus(), is(200));

    // then
    final Response getReportsResponse =
      embeddedOptimizeExtensionRule
        .getRequestExecutor()
        .buildGetAllReportsRequest()
        .withGivenAuthToken(secondToken)
        .execute();

    assertThat(getReportsResponse.getStatus(), is(200));
  }

  @Test
  public void logoutCreatesTerminatedSessionEntry() {
    // given
    addAdminUserAndGrantAccessPermission();

    final String token = authenticateAdminUser();

    // when
    embeddedOptimizeExtensionRule.getRequestExecutor().buildLogOutRequest().withGivenAuthToken(token).execute();

    // then
    assertThat(elasticSearchIntegrationTestExtensionRule.getDocumentCountOf(TERMINATED_USER_SESSION_INDEX_NAME), is(1));
  }

  @Test
  public void logoutInvalidatesToken() {
    // given
    addAdminUserAndGrantAccessPermission();

    final String token = authenticateAdminUser();

    // when
    final Response logoutResponse =
      embeddedOptimizeExtensionRule
        .getRequestExecutor()
        .buildLogOutRequest()
        .withGivenAuthToken(token)
        .execute();
    assertThat(logoutResponse.getStatus(), is(200));

    // then
    final Response getReportsResponse =
      embeddedOptimizeExtensionRule
        .getRequestExecutor()
        .buildGetAllReportsRequest()
        .withGivenAuthToken(token)
        .execute();

    assertThat(getReportsResponse.getStatus(), is(401));
  }

  @Test
  public void logoutInvalidatesAllTokensOfASession() {
    // given
    int expiryMinutes = embeddedOptimizeExtensionRule.getConfigurationService().getTokenLifeTimeMinutes();
    engineIntegrationExtensionRule.addUser("genzo", "genzo");
    engineIntegrationExtensionRule.grantUserOptimizeAccess("genzo");

    String firstToken = embeddedOptimizeExtensionRule.authenticateUser("genzo", "genzo");

    // when
    // modify time to get a new token for same session
    LocalDateUtil.setCurrentTime(LocalDateUtil.getCurrentDateTime().plusMinutes(expiryMinutes * 2 / 3));
    final Response getNewAuthTokenForSameSessionResponse = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildAuthTestRequest()
      .withGivenAuthToken(firstToken)
      .execute();

    assertThat(getNewAuthTokenForSameSessionResponse.getStatus(), is(200));
    LocalDateUtil.reset();

    final NewCookie newAuthCookie = getNewAuthTokenForSameSessionResponse.getCookies().get(OPTIMIZE_AUTHORIZATION);
    final String newToken = newAuthCookie.getValue().replace(AUTH_COOKIE_TOKEN_VALUE_PREFIX, "");

    final Response logoutResponse =
      embeddedOptimizeExtensionRule
        .getRequestExecutor()
        .buildLogOutRequest()
        .withGivenAuthToken(firstToken)
        .execute();
    assertThat(logoutResponse.getStatus(), is(200));

    //then
    final Response getReportsResponse =
      embeddedOptimizeExtensionRule
        .getRequestExecutor()
        .buildGetAllReportsRequest()
        .withGivenAuthToken(newToken)
        .execute();

    assertThat(getReportsResponse.getStatus(), is(401));
  }

  @Test
  public void tokenShouldExpireAfterConfiguredTime() {
    // given
    int expiryTime = embeddedOptimizeExtensionRule.getConfigurationService().getTokenLifeTimeMinutes();
    engineIntegrationExtensionRule.addUser("genzo", "genzo");
    engineIntegrationExtensionRule.grantUserOptimizeAccess("genzo");
    String firstToken = embeddedOptimizeExtensionRule.authenticateUser("genzo", "genzo");

    Response testAuthenticationResponse = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildAuthTestRequest()
      .withGivenAuthToken(firstToken)
      .execute();
    assertThat(testAuthenticationResponse.getStatus(), is(200));

    // when
    LocalDateUtil.setCurrentTime(get1MinuteAfterExpiryTime(expiryTime));
    testAuthenticationResponse = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildAuthTestRequest()
      .withGivenAuthToken(firstToken)
      .execute();

    //then
    assertThat(testAuthenticationResponse.getStatus(), is(401));
  }

  @Test
  public void authCookieIsExtendedByRequestInLastThirdOfLifeTime() {
    // given
    int expiryMinutes = embeddedOptimizeExtensionRule.getConfigurationService().getTokenLifeTimeMinutes();
    engineIntegrationExtensionRule.addUser("genzo", "genzo");
    engineIntegrationExtensionRule.grantUserOptimizeAccess("genzo");
    String firstToken = embeddedOptimizeExtensionRule.authenticateUser("genzo", "genzo");

    Response testAuthenticationResponse = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildAuthTestRequest()
      .withGivenAuthToken(firstToken)
      .execute();
    assertThat(testAuthenticationResponse.getStatus(), is(200));

    // when
    final OffsetDateTime dateTimeBeforeRefresh = LocalDateUtil.getCurrentDateTime();
    LocalDateUtil.setCurrentTime(LocalDateUtil.getCurrentDateTime().plusMinutes(expiryMinutes * 2 / 3));
    testAuthenticationResponse = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildAuthTestRequest()
      .withGivenAuthToken(firstToken)
      .execute();

    //then
    assertThat(testAuthenticationResponse.getStatus(), is(200));
    assertThat(testAuthenticationResponse.getCookies().keySet(), hasItem(OPTIMIZE_AUTHORIZATION));
    final NewCookie newAuthCookie = testAuthenticationResponse.getCookies().get(OPTIMIZE_AUTHORIZATION);
    final String newToken = newAuthCookie.getValue().replace(AUTH_COOKIE_TOKEN_VALUE_PREFIX, "");
    assertThat(newToken, is(not(equalTo(firstToken))));
    assertThat(
      newAuthCookie.getExpiry().toInstant(),
      is(greaterThan(dateTimeBeforeRefresh.plusMinutes(expiryMinutes).toInstant()))
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
      elasticSearchIntegrationTestExtensionRule.deleteIndexOfMapping(new TerminatedUserSessionIndex());

      final Response getReportsResponse =
        embeddedOptimizeExtensionRule
          .getRequestExecutor()
          .buildGetAllReportsRequest()
          .withGivenAuthToken(token)
          .execute();

      // then

      assertThat(getReportsResponse.getStatus(), is(200));
    } finally {
      embeddedOptimizeExtensionRule.getElasticSearchSchemaManager().initializeSchema(
        embeddedOptimizeExtensionRule.getOptimizeElasticClient()
      );
    }
  }

  private OffsetDateTime get1MinuteAfterExpiryTime(int expiryTime) {
    return LocalDateUtil.getCurrentDateTime().plusMinutes(expiryTime + 1);
  }

  private String authenticateAdminUser() {
    return embeddedOptimizeExtensionRule.authenticateUser("admin", "admin");
  }

  private void addAdminUserAndGrantAccessPermission() {
    engineIntegrationExtensionRule.addUser("admin", "admin");
    engineIntegrationExtensionRule.grantUserOptimizeAccess("admin");
  }

  private TerminatedSessionService getTerminatedSessionService() {
    return embeddedOptimizeExtensionRule.getApplicationContext().getBean(TerminatedSessionService.class);
  }
}
