package org.camunda.optimize.service.security;

public class Session {

  private TokenVerifier tokenVerifier;

  Session(TokenVerifier tokenVerifier) {
    this.tokenVerifier = tokenVerifier;
  }

  public void updateExpiryDate() {
    tokenVerifier.updateExpiryDate();
  }

  public boolean isTokenValid(String token) {
    return tokenVerifier.isTokenValid(token);
  }

  public boolean hasTokenExpired(String token) {
    return tokenVerifier.hasExpired(token);
  }

}
