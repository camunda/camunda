/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.definition;

public class DefinitionKeyResponseDto {

  private String key;
  private String name;

  public DefinitionKeyResponseDto(final String key, final String name) {
    this.key = key;
    this.name = name;
  }

  protected DefinitionKeyResponseDto() {}

  public String getKey() {
    return key;
  }

  public void setKey(final String key) {
    this.key = key;
  }

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof DefinitionKeyResponseDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $key = getKey();
    result = result * PRIME + ($key == null ? 43 : $key.hashCode());
    final Object $name = getName();
    result = result * PRIME + ($name == null ? 43 : $name.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof DefinitionKeyResponseDto)) {
      return false;
    }
    final DefinitionKeyResponseDto other = (DefinitionKeyResponseDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$key = getKey();
    final Object other$key = other.getKey();
    if (this$key == null ? other$key != null : !this$key.equals(other$key)) {
      return false;
    }
    final Object this$name = getName();
    final Object other$name = other.getName();
    if (this$name == null ? other$name != null : !this$name.equals(other$name)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "DefinitionKeyResponseDto(key=" + getKey() + ", name=" + getName() + ")";
  }

  public static DefinitionKeyResponseDtoBuilder builder() {
    return new DefinitionKeyResponseDtoBuilder();
  }

  public static class DefinitionKeyResponseDtoBuilder {

    private String key;
    private String name;

    DefinitionKeyResponseDtoBuilder() {}

    public DefinitionKeyResponseDtoBuilder key(final String key) {
      this.key = key;
      return this;
    }

    public DefinitionKeyResponseDtoBuilder name(final String name) {
      this.name = name;
      return this;
    }

    public DefinitionKeyResponseDto build() {
      return new DefinitionKeyResponseDto(key, name);
    }

    @Override
    public String toString() {
      return "DefinitionKeyResponseDto.DefinitionKeyResponseDtoBuilder(key="
          + key
          + ", name="
          + name
          + ")";
    }
  }
}
