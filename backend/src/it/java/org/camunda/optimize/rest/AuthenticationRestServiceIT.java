/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.AbstractIT;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class AuthenticationRestServiceIT extends AbstractIT {

  @Test
  public void authenticateUser() {
    // given
    addAdminUserAndGrantAccessPermission();

    //when
    Response response = embeddedOptimizeExtension.authenticateUserRequest("admin", "admin");

    //then
    assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
    String responseEntity = response.readEntity(String.class);
    assertThat(responseEntity, is(notNullValue()));
  }

  @Test
  public void logout() {
    //given
    addAdminUserAndGrantAccessPermission();
    String token = authenticateAdminUser();

    //when
    Response logoutResponse = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildLogOutRequest()
      .withGivenAuthToken(token)
      .execute();

    //then
    assertThat(logoutResponse.getStatus(), is(Response.Status.OK.getStatusCode()));
    String responseEntity = logoutResponse.readEntity(String.class);
    assertThat(responseEntity, is("OK"));
  }

  @Test
  public void logoutSecure() {

    //when
    Response logoutResponse = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildLogOutRequest()
      .withGivenAuthToken("randomToken")
      .execute();

    //then
    assertThat(logoutResponse.getStatus(), is(Response.Status.UNAUTHORIZED.getStatusCode()));
  }

  @Test
  public void testAuthenticationIfNotAuthenticated() {
    //when
    Response logoutResponse = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildAuthTestRequest()
      .withoutAuthentication()
      .execute();

    //then
    assertThat(logoutResponse.getStatus(), is(Response.Status.UNAUTHORIZED.getStatusCode()));
  }

  @Test
  public void testIfAuthenticated() {
    //when
    Response logoutResponse = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildAuthTestRequest()
      .execute();

    //then
    assertThat(logoutResponse.getStatus(), is(Response.Status.OK.getStatusCode()));
  }

  private String authenticateAdminUser() {
    return embeddedOptimizeExtension.authenticateUser("admin", "admin");
  }

  private void addAdminUserAndGrantAccessPermission() {
    engineIntegrationExtension.addUser("admin", "admin");
    engineIntegrationExtension.grantUserOptimizeAccess("admin");
  }

}