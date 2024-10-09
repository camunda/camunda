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
    final int PRIME = 59;
    int result = 1;
    final Object $authenticatedUser = getAuthenticatedUser();
    result = result * PRIME + ($authenticatedUser == null ? 43 : $authenticatedUser.hashCode());
    result = result * PRIME + (isAuthenticated() ? 79 : 97);
    final Object $engineAlias = getEngineAlias();
    result = result * PRIME + ($engineAlias == null ? 43 : $engineAlias.hashCode());
    final Object $errorMessage = getErrorMessage();
    result = result * PRIME + ($errorMessage == null ? 43 : $errorMessage.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof AuthenticationResultDto)) {
      return false;
    }
    final AuthenticationResultDto other = (AuthenticationResultDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$authenticatedUser = getAuthenticatedUser();
    final Object other$authenticatedUser = other.getAuthenticatedUser();
    if (this$authenticatedUser == null
        ? other$authenticatedUser != null
        : !this$authenticatedUser.equals(other$authenticatedUser)) {
      return false;
    }
    if (isAuthenticated() != other.isAuthenticated()) {
      return false;
    }
    final Object this$engineAlias = getEngineAlias();
    final Object other$engineAlias = other.getEngineAlias();
    if (this$engineAlias == null
        ? other$engineAlias != null
        : !this$engineAlias.equals(other$engineAlias)) {
      return false;
    }
    final Object this$errorMessage = getErrorMessage();
    final Object other$errorMessage = other.getErrorMessage();
    if (this$errorMessage == null
        ? other$errorMessage != null
        : !this$errorMessage.equals(other$errorMessage)) {
      return false;
    }
    return true;
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
