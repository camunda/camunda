/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.rolemanagement.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;

@Entity
@Table(name = "permissions")
public class Permission {

  @Id private Long id;

  @NotNull private String audience;

  @NotNull private String definition;

  private String description;

  public Permission(
      final Long id, final String audience, final String definition, final String description) {
    this.id = id;
    this.audience = audience;
    this.definition = definition;
    this.description = description;
  }

  public Permission() {}

  public Long getId() {
    return id;
  }

  public void setId(final Long id) {
    this.id = id;
  }

  public String getAudience() {
    return audience;
  }

  public void setAudience(final String audience) {
    this.audience = audience;
  }

  public String getDefinition() {
    return definition;
  }

  public void setDefinition(final String definition) {
    this.definition = definition;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(final String description) {
    this.description = description;
  }

  @Override
  public int hashCode() {
    return Objects.hash(getId(), getAudience(), getDefinition(), getDescription());
  }

  @Override
  public boolean equals(final Object that) {
    if (this == that) {
      return true;
    }
    if (that == null || getClass() != that.getClass()) {
      return false;
    }
    final Permission thatPermission = (Permission) that;
    return Objects.equals(getId(), thatPermission.getId())
        && Objects.equals(getAudience(), thatPermission.getAudience())
        && Objects.equals(getDefinition(), thatPermission.getDefinition())
        && Objects.equals(getDescription(), thatPermission.getDescription());
  }
}
