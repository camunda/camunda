/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import org.camunda.optimize.service.es.schema.type.TerminatedUserSessionType;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineDatabaseRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

import static org.camunda.optimize.service.security.AuthCookieService.AUTH_COOKIE_TOKEN_VALUE_PREFIX;
import static org.camunda.optimize.service.security.AuthCookieService.OPTIMIZE_AUTHORIZATION;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TERMINATED_USER_SESSION_TYPE;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;

public class SessionServiceIT {
  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  public EngineDatabaseRule engineDatabaseRule = new EngineDatabaseRule();

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule).around(engineDatabaseRule);

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
    embeddedOptimizeRule.getRequestExecutor().buildLogOutRequest().withGivenAuthToken(token).execute();

    LocalDateUtil.setCurrentTime(OffsetDateTime.now().plus(
      embeddedOptimizeRule.getConfigurationService().getTokenLifeTimeMinutes(),
      ChronoUnit.MINUTES
    ));
    getTerminatedSessionService().cleanup();

    // then
    assertThat(elasticSearchRule.getDocumentCountOf(TERMINATED_USER_SESSION_TYPE), is(0));
  }

  @Test
  public void authenticatingSameUserTwiceCreatesNewIndependentSession() {
    // given
    addAdminUserAndGrantAccessPermission();

    final String firstToken = authenticateAdminUser();
    final String secondToken = authenticateAdminUser();

    // when
    final Response logoutResponse =
      embeddedOptimizeRule
        .getRequestExecutor()
        .buildLogOutRequest()
        .withGivenAuthToken(firstToken)
        .execute();
    Assert.assertThat(logoutResponse.getStatus(), is(200));

    // then
    final Response getReportsResponse =
      embeddedOptimizeRule
        .getRequestExecutor()
        .buildGetAllReportsRequest()
        .withGivenAuthToken(secondToken)
        .execute();

    Assert.assertThat(getReportsResponse.getStatus(), is(200));
  }

  @Test
  public void logoutCreatesTerminatedSessionEntry() {
    // given
    addAdminUserAndGrantAccessPermission();

    final String token = authenticateAdminUser();

    // when
    embeddedOptimizeRule.getRequestExecutor().buildLogOutRequest().withGivenAuthToken(token).execute();

    // then
    assertThat(elasticSearchRule.getDocumentCountOf(TERMINATED_USER_SESSION_TYPE), is(1));
  }

  @Test
  public void logoutInvalidatesToken() {
    // given
    addAdminUserAndGrantAccessPermission();

    final String token = authenticateAdminUser();

    // when
    final Response logoutResponse =
      embeddedOptimizeRule
        .getRequestExecutor()
        .buildLogOutRequest()
        .withGivenAuthToken(token)
        .execute();
    Assert.assertThat(logoutResponse.getStatus(), is(200));

    // then
    final Response getReportsResponse =
      embeddedOptimizeRule
        .getRequestExecutor()
        .buildGetAllReportsRequest()
        .withGivenAuthToken(token)
        .execute();

    Assert.assertThat(getReportsResponse.getStatus(), is(401));
  }

  @Test
  public void logoutInvalidatesAllTokensOfASession() {
    // given
    int expiryMinutes = embeddedOptimizeRule.getConfigurationService().getTokenLifeTimeMinutes();
    engineRule.addUser("genzo", "genzo");
    engineRule.grantUserOptimizeAccess("genzo");

    String firstToken = embeddedOptimizeRule.authenticateUser("genzo", "genzo");

    // when
    // modify time to get a new token for same session
    LocalDateUtil.setCurrentTime(LocalDateUtil.getCurrentDateTime().plusMinutes(expiryMinutes * 2 / 3));
    final Response getNewAuthTokenForSameSessionResponse = embeddedOptimizeRule
      .getRequestExecutor()
      .buildAuthTestRequest()
      .withGivenAuthToken(firstToken)
      .execute();

    Assert.assertThat(getNewAuthTokenForSameSessionResponse.getStatus(), is(200));
    LocalDateUtil.reset();

    final NewCookie newAuthCookie = getNewAuthTokenForSameSessionResponse.getCookies().get(OPTIMIZE_AUTHORIZATION);
    final String newToken = newAuthCookie.getValue().replace(AUTH_COOKIE_TOKEN_VALUE_PREFIX, "");

    final Response logoutResponse =
      embeddedOptimizeRule
        .getRequestExecutor()
        .buildLogOutRequest()
        .withGivenAuthToken(firstToken)
        .execute();
    Assert.assertThat(logoutResponse.getStatus(), is(200));

    //then
    final Response getReportsResponse =
      embeddedOptimizeRule
        .getRequestExecutor()
        .buildGetAllReportsRequest()
        .withGivenAuthToken(newToken)
        .execute();

    Assert.assertThat(getReportsResponse.getStatus(), is(401));
  }

  @Test
  public void tokenShouldExpireAfterConfiguredTime() {
    // given
    int expiryTime = embeddedOptimizeRule.getConfigurationService().getTokenLifeTimeMinutes();
    engineRule.addUser("genzo", "genzo");
    engineRule.grantUserOptimizeAccess("genzo");
    String firstToken = embeddedOptimizeRule.authenticateUser("genzo", "genzo");

    Response testAuthenticationResponse = embeddedOptimizeRule
      .getRequestExecutor()
      .buildAuthTestRequest()
      .withGivenAuthToken(firstToken)
      .execute();
    Assert.assertThat(testAuthenticationResponse.getStatus(), is(200));

    // when
    LocalDateUtil.setCurrentTime(get1MinuteAfterExpiryTime(expiryTime));
    testAuthenticationResponse = embeddedOptimizeRule
      .getRequestExecutor()
      .buildAuthTestRequest()
      .withGivenAuthToken(firstToken)
      .execute();

    //then
    Assert.assertThat(testAuthenticationResponse.getStatus(), is(401));
  }

  @Test
  public void authCookieIsExtendedByRequestInLastThirdOfLifeTime() {
    // given
    int expiryMinutes = embeddedOptimizeRule.getConfigurationService().getTokenLifeTimeMinutes();
    engineRule.addUser("genzo", "genzo");
    engineRule.grantUserOptimizeAccess("genzo");
    String firstToken = embeddedOptimizeRule.authenticateUser("genzo", "genzo");

    Response testAuthenticationResponse = embeddedOptimizeRule
      .getRequestExecutor()
      .buildAuthTestRequest()
      .withGivenAuthToken(firstToken)
      .execute();
    Assert.assertThat(testAuthenticationResponse.getStatus(), is(200));

    // when
    final OffsetDateTime dateTimeBeforeRefresh = LocalDateUtil.getCurrentDateTime();
    LocalDateUtil.setCurrentTime(LocalDateUtil.getCurrentDateTime().plusMinutes(expiryMinutes * 2 / 3));
    testAuthenticationResponse = embeddedOptimizeRule
      .getRequestExecutor()
      .buildAuthTestRequest()
      .withGivenAuthToken(firstToken)
      .execute();

    //then
    Assert.assertThat(testAuthenticationResponse.getStatus(), is(200));
    Assert.assertThat(testAuthenticationResponse.getCookies().keySet(), hasItem(OPTIMIZE_AUTHORIZATION));
    final NewCookie newAuthCookie = testAuthenticationResponse.getCookies().get(OPTIMIZE_AUTHORIZATION);
    final String newToken = newAuthCookie.getValue().replace(AUTH_COOKIE_TOKEN_VALUE_PREFIX, "");
    Assert.assertThat(newToken, is(not(equalTo(firstToken))));
    Assert.assertThat(
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
      elasticSearchRule.deleteIndexOfType(new TerminatedUserSessionType());

      final Response getReportsResponse =
        embeddedOptimizeRule
          .getRequestExecutor()
          .buildGetAllReportsRequest()
          .withGivenAuthToken(token)
          .execute();

      // then

      Assert.assertThat(getReportsResponse.getStatus(), is(200));
    } finally {
      embeddedOptimizeRule.getElasticSearchSchemaManager().initializeSchema(
        embeddedOptimizeRule.getElasticsearchClient()
      );
    }
  }

  private OffsetDateTime get1MinuteAfterExpiryTime(int expiryTime) {
    return LocalDateUtil.getCurrentDateTime().plusMinutes(expiryTime + 1);
  }

  private String authenticateAdminUser() {
    return embeddedOptimizeRule.authenticateUser("admin", "admin");
  }

  private void addAdminUserAndGrantAccessPermission() {
    engineRule.addUser("admin", "admin");
    engineRule.grantUserOptimizeAccess("admin");
  }

  private TerminatedSessionService getTerminatedSessionService() {
    return embeddedOptimizeRule.getApplicationContext().getBean(TerminatedSessionService.class);
  }
}
