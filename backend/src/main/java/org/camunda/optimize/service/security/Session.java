/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
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
