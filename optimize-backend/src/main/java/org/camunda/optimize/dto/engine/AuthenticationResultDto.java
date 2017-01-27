package org.camunda.optimize.dto.engine;

public class AuthenticationResultDto {

  protected String authenticatedUser;
  protected boolean isAuthenticated;

  public String getAuthenticatedUser() {
    return authenticatedUser;
  }

  public void setAuthenticatedUser(String authenticatedUser) {
    this.authenticatedUser = authenticatedUser;
  }

  public boolean isAuthenticated() {
    return isAuthenticated;
  }

  public void setAuthenticated(boolean authenticated) {
    isAuthenticated = authenticated;
  }
}
