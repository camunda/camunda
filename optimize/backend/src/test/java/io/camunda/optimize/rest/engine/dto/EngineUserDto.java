/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.engine.dto;

import java.util.Objects;

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
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final EngineUserDto that = (EngineUserDto) o;
    return Objects.equals(profile, that.profile) && Objects.equals(credentials, that.credentials);
  }

  @Override
  public int hashCode() {
    return Objects.hash(profile, credentials);
  }

  @Override
  public String toString() {
    return "EngineUserDto(profile=" + getProfile() + ", credentials=" + getCredentials() + ")";
  }
}
