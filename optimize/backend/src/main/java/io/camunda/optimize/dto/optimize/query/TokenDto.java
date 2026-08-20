/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query;

import java.util.Objects;

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
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final TokenDto tokenDto = (TokenDto) o;
    return Objects.equals(token, tokenDto.token);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(token);
  }

  @Override
  public String toString() {
    return "TokenDto(token=" + getToken() + ")";
  }
}
