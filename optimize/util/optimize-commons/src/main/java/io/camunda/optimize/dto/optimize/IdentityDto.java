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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public String toString() {
    return "IdentityDto(id=" + getId() + ", type=" + getType() + ")";
  }

  @SuppressWarnings("checkstyle:ConstantName")
  public static final class Fields {

    public static final String id = "id";
    public static final String type = "type";
  }
}
