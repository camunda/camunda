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
