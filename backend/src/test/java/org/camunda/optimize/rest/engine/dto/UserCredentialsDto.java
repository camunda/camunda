/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest.engine.dto;

public class UserCredentialsDto {

  protected String password;
  protected String authenticatedUserPassword;

  // getters / setters /////////////////////////////

  public String getPassword() {
    return password;
  }
  public void setPassword(String password) {
    this.password = password;
  }

  public String getAuthenticatedUserPassword() {
    return authenticatedUserPassword;
  }
  public void setAuthenticatedUserPassword(String authenticatedUserPassword) {
    this.authenticatedUserPassword = authenticatedUserPassword;
  }

}
