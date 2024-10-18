/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.engine.dto;

public class EngineUserDto {

  private UserProfileDto profile;
  private UserCredentialsDto credentials;

  public EngineUserDto(final UserProfileDto profile, final UserCredentialsDto credentials) {
    this.profile = profile;
    this.credentials = credentials;
  }

  protected EngineUserDto() {}

  public UserProfileDto getProfile() {
    return profile;
  }

  public void setProfile(final UserProfileDto profile) {
    this.profile = profile;
  }

  public UserCredentialsDto getCredentials() {
    return credentials;
  }

  public void setCredentials(final UserCredentialsDto credentials) {
    this.credentials = credentials;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof EngineUserDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $profile = getProfile();
    result = result * PRIME + ($profile == null ? 43 : $profile.hashCode());
    final Object $credentials = getCredentials();
    result = result * PRIME + ($credentials == null ? 43 : $credentials.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof EngineUserDto)) {
      return false;
    }
    final EngineUserDto other = (EngineUserDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$profile = getProfile();
    final Object other$profile = other.getProfile();
    if (this$profile == null ? other$profile != null : !this$profile.equals(other$profile)) {
      return false;
    }
    final Object this$credentials = getCredentials();
    final Object other$credentials = other.getCredentials();
    if (this$credentials == null
        ? other$credentials != null
        : !this$credentials.equals(other$credentials)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "EngineUserDto(profile=" + getProfile() + ", credentials=" + getCredentials() + ")";
  }
}
