/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;

import jakarta.ws.rs.core.Response;
import org.camunda.optimize.AbstractPlatformIT;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag(OPENSEARCH_PASSING)
public class AuthenticationRestServiceIT extends AbstractPlatformIT {

  @Test
  public void authenticateUser() {
    // given
    addAdminUserAndGrantAccessPermission();

    // when
    Response response = embeddedOptimizeExtension.authenticateUserRequest("admin", "admin");

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    String responseEntity = response.readEntity(String.class);
    assertThat(responseEntity).isNotNull();
  }

  @Test
  public void logout() {
    // given
    addAdminUserAndGrantAccessPermission();
    String token = authenticateAdminUser();

    // when
    Response logoutResponse =
        embeddedOptimizeExtension
            .getRequestExecutor()
            .buildLogOutRequest()
            .withGivenAuthToken(token)
            .execute();

    // then
    assertThat(logoutResponse.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    String responseEntity = logoutResponse.readEntity(String.class);
    assertThat(responseEntity).isEqualTo("OK");
  }

  @Test
  public void logoutSecure() {

    // when
    Response logoutResponse =
        embeddedOptimizeExtension
            .getRequestExecutor()
            .buildLogOutRequest()
            .withGivenAuthToken("randomToken")
            .execute();

    // then
    assertThat(logoutResponse.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void testAuthenticationIfNotAuthenticated() {
    // when
    Response logoutResponse =
        embeddedOptimizeExtension
            .getRequestExecutor()
            .buildAuthTestRequest()
            .withoutAuthentication()
            .execute();

    // then
    assertThat(logoutResponse.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void testIfAuthenticated() {
    // when
    Response logoutResponse =
        embeddedOptimizeExtension.getRequestExecutor().buildAuthTestRequest().execute();

    // then
    assertThat(logoutResponse.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  private String authenticateAdminUser() {
    return embeddedOptimizeExtension.authenticateUser("admin", "admin");
  }

  private void addAdminUserAndGrantAccessPermission() {
    engineIntegrationExtension.addUser("admin", "admin");
    engineIntegrationExtension.grantUserOptimizeAccess("admin");
  }
}
