/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.definition;

import io.camunda.optimize.dto.optimize.SimpleDefinitionDto;
import java.util.List;
import java.util.Objects;

public class TenantWithDefinitionsResponseDto {

  private String id;
  private String name;
  private List<SimpleDefinitionDto> definitions;

  public TenantWithDefinitionsResponseDto(
      final String id, final String name, final List<SimpleDefinitionDto> definitions) {
    if (definitions == null) {
      throw new IllegalArgumentException("definitions cannot be null");
    }

    this.id = id;
    this.name = name;
    this.definitions = definitions;
  }

  protected TenantWithDefinitionsResponseDto() {}

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public List<SimpleDefinitionDto> getDefinitions() {
    return definitions;
  }

  public void setDefinitions(final List<SimpleDefinitionDto> definitions) {
    if (definitions == null) {
      throw new IllegalArgumentException("definitions cannot be null");
    }

    this.definitions = definitions;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof TenantWithDefinitionsResponseDto;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final TenantWithDefinitionsResponseDto that = (TenantWithDefinitionsResponseDto) o;
    return Objects.equals(id, that.id)
        && Objects.equals(name, that.name)
        && Objects.equals(definitions, that.definitions);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name, definitions);
  }

  @Override
  public String toString() {
    return "TenantWithDefinitionsResponseDto(id="
        + getId()
        + ", name="
        + getName()
        + ", definitions="
        + getDefinitions()
        + ")";
  }
}
