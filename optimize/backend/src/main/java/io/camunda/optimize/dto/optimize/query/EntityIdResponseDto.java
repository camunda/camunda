/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query;

import io.camunda.optimize.dto.optimize.query.entity.EntityType;
import java.util.Objects;

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
    return Objects.hash(id, entityType);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final EntityIdResponseDto that = (EntityIdResponseDto) o;
    return Objects.equals(id, that.id) && Objects.equals(entityType, that.entityType);
  }

  @Override
  public String toString() {
    return "EntityIdResponseDto(id=" + getId() + ", entityType=" + getEntityType() + ")";
  }
}
