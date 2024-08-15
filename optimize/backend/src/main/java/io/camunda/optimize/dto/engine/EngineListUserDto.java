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
    final int PRIME = 59;
    int result = 1;
    final Object $id = getId();
    result = result * PRIME + ($id == null ? 43 : $id.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof EngineListUserDto)) {
      return false;
    }
    final EngineListUserDto other = (EngineListUserDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$id = getId();
    final Object other$id = other.getId();
    if (this$id == null ? other$id != null : !this$id.equals(other$id)) {
      return false;
    }
    return true;
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
