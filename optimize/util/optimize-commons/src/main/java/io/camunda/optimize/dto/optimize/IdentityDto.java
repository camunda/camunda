/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize;

public class IdentityDto {

  private String id;
  private IdentityType type;

  public IdentityDto(final String id, final IdentityType type) {
    if (id == null) {
      throw new IllegalArgumentException("id cannot be null");
    }

    this.id = id;
    this.type = type;
  }

  protected IdentityDto() {}

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    if (id == null) {
      throw new IllegalArgumentException("id cannot be null");
    }

    this.id = id;
  }

  public IdentityType getType() {
    return type;
  }

  public void setType(final IdentityType type) {
    this.type = type;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof IdentityDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $id = getId();
    result = result * PRIME + ($id == null ? 43 : $id.hashCode());
    final Object $type = getType();
    result = result * PRIME + ($type == null ? 43 : $type.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof IdentityDto)) {
      return false;
    }
    final IdentityDto other = (IdentityDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$id = getId();
    final Object other$id = other.getId();
    if (this$id == null ? other$id != null : !this$id.equals(other$id)) {
      return false;
    }
    final Object this$type = getType();
    final Object other$type = other.getType();
    if (this$type == null ? other$type != null : !this$type.equals(other$type)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "IdentityDto(id=" + getId() + ", type=" + getType() + ")";
  }

  public static final class Fields {

    public static final String id = "id";
    public static final String type = "type";
  }
}
