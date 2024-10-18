/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.engine;

public class EngineListUserDto {

  private String id;
  private String firstName;
  private String lastName;
  private String email;

  public EngineListUserDto(
      final String id, final String firstName, final String lastName, final String email) {
    this.id = id;
    this.firstName = firstName;
    this.lastName = lastName;
    this.email = email;
  }

  protected EngineListUserDto() {}

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
    return other instanceof EngineListUserDto;
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
    return "EngineListUserDto(id="
        + getId()
        + ", firstName="
        + getFirstName()
        + ", lastName="
        + getLastName()
        + ", email="
        + getEmail()
        + ")";
  }

  public static EngineListUserDtoBuilder builder() {
    return new EngineListUserDtoBuilder();
  }

  public static class EngineListUserDtoBuilder {

    private String id;
    private String firstName;
    private String lastName;
    private String email;

    EngineListUserDtoBuilder() {}

    public EngineListUserDtoBuilder id(final String id) {
      this.id = id;
      return this;
    }

    public EngineListUserDtoBuilder firstName(final String firstName) {
      this.firstName = firstName;
      return this;
    }

    public EngineListUserDtoBuilder lastName(final String lastName) {
      this.lastName = lastName;
      return this;
    }

    public EngineListUserDtoBuilder email(final String email) {
      this.email = email;
      return this;
    }

    public EngineListUserDto build() {
      return new EngineListUserDto(id, firstName, lastName, email);
    }

    @Override
    public String toString() {
      return "EngineListUserDto.EngineListUserDtoBuilder(id="
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
