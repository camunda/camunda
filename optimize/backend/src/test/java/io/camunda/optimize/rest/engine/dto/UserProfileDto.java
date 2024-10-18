/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.engine.dto;

public class UserProfileDto {

  protected String id;
  protected String firstName;
  protected String lastName;
  protected String email;

  public UserProfileDto(
      final String id, final String firstName, final String lastName, final String email) {
    this.id = id;
    this.firstName = firstName;
    this.lastName = lastName;
    this.email = email;
  }

  protected UserProfileDto() {}

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public String getFirstName() {
    return firstName;
  }

  public void setFirstName(final String firstName) {
    this.firstName = firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public void setLastName(final String lastName) {
    this.lastName = lastName;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(final String email) {
    this.email = email;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof UserProfileDto;
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
    return "UserProfileDto(id="
        + getId()
        + ", firstName="
        + getFirstName()
        + ", lastName="
        + getLastName()
        + ", email="
        + getEmail()
        + ")";
  }

  public static UserProfileDtoBuilder builder() {
    return new UserProfileDtoBuilder();
  }

  public static class UserProfileDtoBuilder {

    private String id;
    private String firstName;
    private String lastName;
    private String email;

    UserProfileDtoBuilder() {}

    public UserProfileDtoBuilder id(final String id) {
      this.id = id;
      return this;
    }

    public UserProfileDtoBuilder firstName(final String firstName) {
      this.firstName = firstName;
      return this;
    }

    public UserProfileDtoBuilder lastName(final String lastName) {
      this.lastName = lastName;
      return this;
    }

    public UserProfileDtoBuilder email(final String email) {
      this.email = email;
      return this;
    }

    public UserProfileDto build() {
      return new UserProfileDto(id, firstName, lastName, email);
    }

    @Override
    public String toString() {
      return "UserProfileDto.UserProfileDtoBuilder(id="
          + id
          + ", firstName="
          + firstName
          + ", lastName="
          + lastName
          + ", email="
          + email
          + ")";
    }
  }
}
