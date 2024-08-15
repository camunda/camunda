/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query;

public class TokenDto {

  protected String token;

  public TokenDto(final String token) {
    this.token = token;
  }

  public TokenDto() {}

  public String getToken() {
    return token;
  }

  public void setToken(final String token) {
    this.token = token;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof TokenDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $token = getToken();
    result = result * PRIME + ($token == null ? 43 : $token.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof TokenDto)) {
      return false;
    }
    final TokenDto other = (TokenDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$token = getToken();
    final Object other$token = other.getToken();
    if (this$token == null ? other$token != null : !this$token.equals(other$token)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "TokenDto(token=" + getToken() + ")";
  }
}
