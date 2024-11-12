/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.collection;

import io.camunda.optimize.dto.optimize.IdentityDto;
import io.camunda.optimize.dto.optimize.RoleType;
import java.util.Optional;

public class CollectionRoleRequestDto {

  private static final String ID_SEGMENT_SEPARATOR = ":";
  private String id;
  private IdentityDto identity;
  private RoleType role;

  public CollectionRoleRequestDto(final IdentityDto identity, final RoleType role) {
    setIdentity(identity);
    this.role = role;
  }

  protected CollectionRoleRequestDto() {}

  public String getId() {
    return Optional.ofNullable(id).orElse(convertIdentityToRoleId(identity));
  }

  protected void setId(final String id) {
    this.id = id;
  }

  private String convertIdentityToRoleId(final IdentityDto identity) {
    return identity.getType() == null
        ? "UNKNOWN" + ID_SEGMENT_SEPARATOR + identity.getId()
        : identity.getType().name() + ID_SEGMENT_SEPARATOR + identity.getId();
  }

  public IdentityDto getIdentity() {
    return identity;
  }

  public void setIdentity(final IdentityDto identity) {
    id = convertIdentityToRoleId(identity);
    this.identity = identity;
  }

  public RoleType getRole() {
    return role;
  }

  public void setRole(final RoleType role) {
    this.role = role;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof CollectionRoleRequestDto;
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
    return "CollectionRoleRequestDto(id="
        + getId()
        + ", identity="
        + getIdentity()
        + ", role="
        + getRole()
        + ")";
  }

  public enum Fields {
    id,
    identity,
    role
  }
}
