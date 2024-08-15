/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query;

import io.camunda.optimize.dto.optimize.query.entity.EntityType;

public class EntityIdResponseDto {

  private String id;
  private EntityType entityType;

  public EntityIdResponseDto(final String id, final EntityType entityType) {
    this.id = id;
    this.entityType = entityType;
  }

  protected EntityIdResponseDto() {}

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public EntityType getEntityType() {
    return entityType;
  }

  public void setEntityType(final EntityType entityType) {
    this.entityType = entityType;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof EntityIdResponseDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $id = getId();
    result = result * PRIME + ($id == null ? 43 : $id.hashCode());
    final Object $entityType = getEntityType();
    result = result * PRIME + ($entityType == null ? 43 : $entityType.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof EntityIdResponseDto)) {
      return false;
    }
    final EntityIdResponseDto other = (EntityIdResponseDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$id = getId();
    final Object other$id = other.getId();
    if (this$id == null ? other$id != null : !this$id.equals(other$id)) {
      return false;
    }
    final Object this$entityType = getEntityType();
    final Object other$entityType = other.getEntityType();
    if (this$entityType == null
        ? other$entityType != null
        : !this$entityType.equals(other$entityType)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "EntityIdResponseDto(id=" + getId() + ", entityType=" + getEntityType() + ")";
  }
}
