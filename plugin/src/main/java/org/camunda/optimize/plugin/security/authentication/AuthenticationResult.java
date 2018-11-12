package org.camunda.optimize.plugin.security.authentication;

public class AuthenticationResult {

  public AuthenticationResult() {
  }

  public AuthenticationResult(boolean isAuthenticated, String authenticatedUser) {
    this.isAuthenticated = isAuthenticated;
    this.authenticatedUser = authenticatedUser;
  }

  private boolean isAuthenticated;
  private String authenticatedUser;

  public boolean isAuthenticated() {
    return isAuthenticated;
  }

  public void setAuthenticated(boolean authenticated) {
    isAuthenticated = authenticated;
  }

  public String getAuthenticatedUser() {
    return authenticatedUser;
  }

  public void setAuthenticatedUser(String authenticatedUser) {
    this.authenticatedUser = authenticatedUser;
  }
}
