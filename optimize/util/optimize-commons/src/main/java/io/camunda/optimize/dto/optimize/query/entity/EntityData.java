/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.entity;

import io.camunda.optimize.dto.optimize.IdentityType;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class EntityData {

  private Map<EntityType, Long> subEntityCounts = new HashMap<>();
  private Map<IdentityType, Long> roleCounts = new HashMap<>();

  public EntityData(final Map<EntityType, Long> subEntityCounts) {
    this.subEntityCounts = subEntityCounts;
  }

  public EntityData(
      final Map<EntityType, Long> subEntityCounts, final Map<IdentityType, Long> roleCounts) {
    this.subEntityCounts = subEntityCounts;
    this.roleCounts = roleCounts;
  }

  public EntityData() {}

  public Map<EntityType, Long> getSubEntityCounts() {
    return subEntityCounts;
  }

  public void setSubEntityCounts(final Map<EntityType, Long> subEntityCounts) {
    this.subEntityCounts = subEntityCounts;
  }

  public Map<IdentityType, Long> getRoleCounts() {
    return roleCounts;
  }

  public void setRoleCounts(final Map<IdentityType, Long> roleCounts) {
    this.roleCounts = roleCounts;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof EntityData;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final EntityData that = (EntityData) o;
    return Objects.equals(subEntityCounts, that.subEntityCounts)
        && Objects.equals(roleCounts, that.roleCounts);
  }

  @Override
  public int hashCode() {
    return Objects.hash(subEntityCounts, roleCounts);
  }

  @Override
  public String toString() {
    return "EntityData(subEntityCounts="
        + getSubEntityCounts()
        + ", roleCounts="
        + getRoleCounts()
        + ")";
  }
}
