/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import org.camunda.optimize.service.AbstractMultiEngineIT;
import org.camunda.optimize.test.engine.AuthorizationClient;
import org.camunda.optimize.test.it.extension.ElasticSearchIntegrationTestExtensionRule;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtensionRule;
import org.camunda.optimize.test.it.extension.EngineIntegrationExtensionRule;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.ws.rs.core.Response;

import static org.camunda.optimize.service.security.EngineAuthenticationProvider.INVALID_CREDENTIALS_ERROR_MESSAGE;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.IsNull.notNullValue;

public class MultiEngineApplicationAuthorizationIT extends AbstractMultiEngineIT {

  @RegisterExtension
  @Order(1)
  public ElasticSearchIntegrationTestExtensionRule elasticSearchIntegrationTestExtensionRule = new ElasticSearchIntegrationTestExtensionRule();
  @RegisterExtension
  @Order(2)
  public EngineIntegrationExtensionRule defaultEngineIntegrationExtensionRule = new EngineIntegrationExtensionRule();
  @RegisterExtension
  @Order(3)
  public EngineIntegrationExtensionRule secondaryEngineIntegrationExtensionRule = new EngineIntegrationExtensionRule("anotherEngine");
  @RegisterExtension
  @Order(4)
  public EmbeddedOptimizeExtensionRule embeddedOptimizeExtensionRule = new EmbeddedOptimizeExtensionRule();

  public AuthorizationClient defaultAuthorizationClient = new AuthorizationClient(defaultEngineIntegrationExtensionRule);
  public AuthorizationClient secondAuthorizationClient = new AuthorizationClient(secondaryEngineIntegrationExtensionRule);

  @Test
  public void authorizedByAtLeastOneEngine() {
    // given
    addSecondEngineToConfiguration();
    defaultAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    defaultEngineIntegrationExtensionRule.addUser("gonzo", "gonzo");
    secondaryEngineIntegrationExtensionRule.addUser(KERMIT_USER, KERMIT_USER);
    secondaryEngineIntegrationExtensionRule.grantUserOptimizeAccess("gonzo");

    // when
    Response response = embeddedOptimizeExtensionRule.authenticateUserRequest(KERMIT_USER, KERMIT_USER);

    // then
    assertThat(response.getStatus(), is(200));
    String responseEntity = response.readEntity(String.class);
    assertThat(responseEntity, is(notNullValue()));

    response = embeddedOptimizeExtensionRule.authenticateUserRequest("gonzo", "gonzo");

    // then
    assertThat(response.getStatus(), is(200));
  }

  @Test
  public void authorizedByOneEngineEvenIfOtherEngineIsDown() {
    // given
    addNonExistingSecondEngineToConfiguration();
    defaultAuthorizationClient.addKermitUserAndGrantAccessToOptimize();

    // when
    Response response = embeddedOptimizeExtensionRule.authenticateUserRequest(KERMIT_USER, KERMIT_USER);

    // then
    assertThat(response.getStatus(), is(200));
  }

  @Test
  public void authorizedByOneEngineEvenIfCredentialsAreWrongForOtherEngine() {
    // given
    addSecondEngineToConfiguration();
    defaultEngineIntegrationExtensionRule.addUser(KERMIT_USER, "123");
    defaultEngineIntegrationExtensionRule.grantUserOptimizeAccess(KERMIT_USER);
    secondAuthorizationClient.addKermitUserAndGrantAccessToOptimize();

    // when
    Response response1 = embeddedOptimizeExtensionRule.authenticateUserRequest(KERMIT_USER, "123");
    secondaryEngineIntegrationExtensionRule.unlockUser(KERMIT_USER);
    Response response2 = embeddedOptimizeExtensionRule.authenticateUserRequest(KERMIT_USER, KERMIT_USER);

    // then
    assertThat(response1.getStatus(), is(200));
    assertThat(response2.getStatus(), is(200));
  }

  @Test
  public void rejectWrongPasswordForAllEngines() {
    // given
    addSecondEngineToConfiguration();
    defaultAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    secondAuthorizationClient.addKermitUserAndGrantAccessToOptimize();

    // when
    Response response = embeddedOptimizeExtensionRule.authenticateUserRequest(KERMIT_USER, "wrongPassword");

    // then
    assertThat(response.getStatus(), is(401));
    String responseEntity = response.readEntity(String.class);
    assertThat(responseEntity, containsString(INVALID_CREDENTIALS_ERROR_MESSAGE));
  }

}
