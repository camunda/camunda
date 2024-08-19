/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize;

import static java.util.stream.Collectors.collectingAndThen;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;

public class UserDto extends IdentityWithMetadataResponseDto {

  private String firstName;
  private String lastName;
  private String email;

  // optional, only available in C8 SaaS
  private List<String> roles;

  public UserDto(final String id) {
    this(id, null, null, null, null);
  }

  public UserDto(final String id, final String firstName) {
    this(id, firstName, null, null, null);
  }

  public UserDto(
      final String id, final String firstName, final String lastName, final String email) {
    this(id, firstName, lastName, email, null);
  }

  public UserDto(
      final String id, final String fullName, final String email, final List<String> roles) {
    this(id, fullName, null, email, roles);
  }

  @JsonCreator
  public UserDto(
      @JsonProperty(required = true, value = "id") final String id,
      @JsonProperty(required = false, value = "firstName") final String firstName,
      @JsonProperty(required = false, value = "lastName") final String lastName,
      @JsonProperty(required = false, value = "email") final String email,
      @JsonProperty(required = false, value = "roles") final List<String> roles) {
    super(id, IdentityType.USER, resolveName(id, firstName, lastName));
    if (id == null) {
      throw new IllegalArgumentException("id cannot be null");
    }

    this.firstName = firstName;
    this.lastName = lastName;
    this.email = email;
    this.roles = roles;
  }

  protected UserDto() {}

  private static String resolveName(
      final String id, final String firstName, final String lastName) {
    return Stream.of(firstName, lastName)
        .filter(Objects::nonNull)
        .collect(
            collectingAndThen(
                Collectors.joining(" "), s -> StringUtils.isNotBlank(s) ? s.trim() : id));
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

  public List<String> getRoles() {
    return roles;
  }

  public void setRoles(final List<String> roles) {
    this.roles = roles;
  }

  @Override
  @JsonIgnore
  public List<Supplier<String>> getSearchableDtoFields() {
    return List.of(
        this::getId, this::getEmail, this::getName, this::getFirstName, this::getLastName);
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof UserDto;
  }

  @Override
  public int hashCode() {
    final int result = super.hashCode();
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof UserDto)) {
      return false;
    }
    final UserDto other = (UserDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "UserDto(super="
        + super.toString()
        + ", firstName="
        + getFirstName()
        + ", lastName="
        + getLastName()
        + ", email="
        + getEmail()
        + ", roles="
        + getRoles()
        + ")";
  }

  public static final class Fields {

    public static final String firstName = "firstName";
    public static final String lastName = "lastName";
    public static final String email = "email";
    public static final String roles = "roles";
  }
}
