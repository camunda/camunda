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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
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
