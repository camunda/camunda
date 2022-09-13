/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.security.authorization;

import org.camunda.optimize.service.AbstractMultiEngineIT;
import org.camunda.optimize.test.engine.AuthorizationClient;
import org.junit.jupiter.api.Test;
import org.mockserver.model.HttpError;

import javax.ws.rs.core.Response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.security.EngineAuthenticationProvider.INVALID_CREDENTIALS_ERROR_MESSAGE;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.mockserver.model.HttpRequest.request;

public class MultiEngineApplicationAuthorizationIT extends AbstractMultiEngineIT {

  private final AuthorizationClient defaultAuthorizationClient = new AuthorizationClient(engineIntegrationExtension);
  private final AuthorizationClient secondAuthorizationClient = new AuthorizationClient(secondaryEngineIntegrationExtension);

  @Test
  public void authorizedByAtLeastOneEngine() {
    // given
    addSecondEngineToConfiguration();
    defaultAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    engineIntegrationExtension.addUser("gonzo", "gonzo");
    secondaryEngineIntegrationExtension.addUser(KERMIT_USER, KERMIT_USER);
    secondaryEngineIntegrationExtension.grantUserOptimizeAccess("gonzo");

    // when
    Response response = embeddedOptimizeExtension.authenticateUserRequest(KERMIT_USER, KERMIT_USER);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    String responseEntity = response.readEntity(String.class);
    assertThat(responseEntity).isNotNull();

    response = embeddedOptimizeExtension.authenticateUserRequest("gonzo", "gonzo");

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @Test
  public void authorizedByOneEngineEvenIfOtherEngineIsDown() {
    // given
    addNonExistingSecondEngineToConfiguration();
    defaultAuthorizationClient.addKermitUserAndGrantAccessToOptimize();

    // when
    Response response = embeddedOptimizeExtension.authenticateUserRequest(KERMIT_USER, KERMIT_USER);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @Test
  public void authorizedByOneEngineEvenIfCredentialsAreWrongForOtherEngine() {
    // given
    addSecondEngineToConfiguration();
    engineIntegrationExtension.addUser(KERMIT_USER, "123");
    engineIntegrationExtension.grantUserOptimizeAccess(KERMIT_USER);
    secondAuthorizationClient.addKermitUserAndGrantAccessToOptimize();

    // when
    Response response1 = embeddedOptimizeExtension.authenticateUserRequest(KERMIT_USER, "123");
    secondaryEngineIntegrationExtension.unlockUser(KERMIT_USER);
    Response response2 = embeddedOptimizeExtension.authenticateUserRequest(KERMIT_USER, KERMIT_USER);

    // then
    assertThat(response1.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(response2.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @Test
  public void rejectWrongPasswordForAllEngines() {
    // given
    addSecondEngineToConfiguration();
    defaultAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    secondAuthorizationClient.addKermitUserAndGrantAccessToOptimize();

    // when
    Response response = embeddedOptimizeExtension.authenticateUserRequest(KERMIT_USER, "wrongPassword");

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
    String responseEntity = response.readEntity(String.class);
    assertThat(responseEntity).contains(INVALID_CREDENTIALS_ERROR_MESSAGE);
  }

  @Test
  public void authorizationFailsWhenAllEnginesDown() {
    // given
    addSecondEngineToConfiguration();
    defaultAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    secondAuthorizationClient.addKermitUserAndGrantAccessToOptimize();

    useAndGetMockServerForEngine(engineIntegrationExtension.getEngineName())
      .when(request().withPath(".*")).error(HttpError.error().withDropConnection(true));
    useAndGetMockServerForEngine(secondaryEngineIntegrationExtension.getEngineName())
      .when(request().withPath(".*")).error(HttpError.error().withDropConnection(true));

    // when
    Response response = embeddedOptimizeExtension.authenticateUserRequest(KERMIT_USER, KERMIT_USER);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

}
