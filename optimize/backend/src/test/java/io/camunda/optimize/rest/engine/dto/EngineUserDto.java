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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public String toString() {
    return "EngineUserDto(profile=" + getProfile() + ", credentials=" + getCredentials() + ")";
  }
}
