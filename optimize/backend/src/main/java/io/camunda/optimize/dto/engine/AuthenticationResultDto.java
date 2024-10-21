/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.engine;

public class AuthenticationResultDto {

  private String authenticatedUser;
  private boolean isAuthenticated;
  private String engineAlias;
  private String errorMessage;

  public AuthenticationResultDto(
      final String authenticatedUser,
      final boolean isAuthenticated,
      final String engineAlias,
      final String errorMessage) {
    this.authenticatedUser = authenticatedUser;
    this.isAuthenticated = isAuthenticated;
    this.engineAlias = engineAlias;
    this.errorMessage = errorMessage;
  }

  protected AuthenticationResultDto() {}

  public String getAuthenticatedUser() {
    return authenticatedUser;
  }

  public void setAuthenticatedUser(final String authenticatedUser) {
    this.authenticatedUser = authenticatedUser;
  }

  public boolean isAuthenticated() {
    return isAuthenticated;
  }

  public void setAuthenticated(final boolean isAuthenticated) {
    this.isAuthenticated = isAuthenticated;
  }

  public String getEngineAlias() {
    return engineAlias;
  }

  public void setEngineAlias(final String engineAlias) {
    this.engineAlias = engineAlias;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(final String errorMessage) {
    this.errorMessage = errorMessage;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof AuthenticationResultDto;
  }

  @Override
  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public String toString() {
    return "AuthenticationResultDto(authenticatedUser="
        + getAuthenticatedUser()
        + ", isAuthenticated="
        + isAuthenticated()
        + ", engineAlias="
        + getEngineAlias()
        + ", errorMessage="
        + getErrorMessage()
        + ")";
  }

  public static AuthenticationResultDtoBuilder builder() {
    return new AuthenticationResultDtoBuilder();
  }

  public static class AuthenticationResultDtoBuilder {

    private String authenticatedUser;
    private boolean isAuthenticated;
    private String engineAlias;
    private String errorMessage;

    AuthenticationResultDtoBuilder() {}

    public AuthenticationResultDtoBuilder authenticatedUser(final String authenticatedUser) {
      this.authenticatedUser = authenticatedUser;
      return this;
    }

    public AuthenticationResultDtoBuilder isAuthenticated(final boolean isAuthenticated) {
      this.isAuthenticated = isAuthenticated;
      return this;
    }

    public AuthenticationResultDtoBuilder engineAlias(final String engineAlias) {
      this.engineAlias = engineAlias;
      return this;
    }

    public AuthenticationResultDtoBuilder errorMessage(final String errorMessage) {
      this.errorMessage = errorMessage;
      return this;
    }

    public AuthenticationResultDto build() {
      return new AuthenticationResultDto(
          authenticatedUser, isAuthenticated, engineAlias, errorMessage);
    }

    @Override
    public String toString() {
      return "AuthenticationResultDto.AuthenticationResultDtoBuilder(authenticatedUser="
          + authenticatedUser
          + ", isAuthenticated="
          + isAuthenticated
          + ", engineAlias="
          + engineAlias
          + ", errorMessage="
          + errorMessage
          + ")";
    }
  }
}
