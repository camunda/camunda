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
    final int PRIME = 59;
    int result = 1;
    final Object $id = getId();
    result = result * PRIME + ($id == null ? 43 : $id.hashCode());
    final Object $firstName = getFirstName();
    result = result * PRIME + ($firstName == null ? 43 : $firstName.hashCode());
    final Object $lastName = getLastName();
    result = result * PRIME + ($lastName == null ? 43 : $lastName.hashCode());
    final Object $email = getEmail();
    result = result * PRIME + ($email == null ? 43 : $email.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof UserProfileDto)) {
      return false;
    }
    final UserProfileDto other = (UserProfileDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$id = getId();
    final Object other$id = other.getId();
    if (this$id == null ? other$id != null : !this$id.equals(other$id)) {
      return false;
    }
    final Object this$firstName = getFirstName();
    final Object other$firstName = other.getFirstName();
    if (this$firstName == null
        ? other$firstName != null
        : !this$firstName.equals(other$firstName)) {
      return false;
    }
    final Object this$lastName = getLastName();
    final Object other$lastName = other.getLastName();
    if (this$lastName == null ? other$lastName != null : !this$lastName.equals(other$lastName)) {
      return false;
    }
    final Object this$email = getEmail();
    final Object other$email = other.getEmail();
    if (this$email == null ? other$email != null : !this$email.equals(other$email)) {
      return false;
    }
    return true;
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
