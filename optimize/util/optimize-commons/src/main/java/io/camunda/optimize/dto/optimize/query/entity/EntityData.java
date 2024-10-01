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
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $subEntityCounts = getSubEntityCounts();
    result = result * PRIME + ($subEntityCounts == null ? 43 : $subEntityCounts.hashCode());
    final Object $roleCounts = getRoleCounts();
    result = result * PRIME + ($roleCounts == null ? 43 : $roleCounts.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof EntityData)) {
      return false;
    }
    final EntityData other = (EntityData) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$subEntityCounts = getSubEntityCounts();
    final Object other$subEntityCounts = other.getSubEntityCounts();
    if (this$subEntityCounts == null
        ? other$subEntityCounts != null
        : !this$subEntityCounts.equals(other$subEntityCounts)) {
      return false;
    }
    final Object this$roleCounts = getRoleCounts();
    final Object other$roleCounts = other.getRoleCounts();
    if (this$roleCounts == null
        ? other$roleCounts != null
        : !this$roleCounts.equals(other$roleCounts)) {
      return false;
    }
    return true;
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
