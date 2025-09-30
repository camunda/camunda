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

public class TenantIdWithDefinitionsDto {

  private String id;
  private List<SimpleDefinitionDto> definitions;

  public TenantIdWithDefinitionsDto(final String id, final List<SimpleDefinitionDto> definitions) {
    if (definitions == null) {
      throw new IllegalArgumentException("definitions cannot be null");
    }

    this.id = id;
    this.definitions = definitions;
  }

  protected TenantIdWithDefinitionsDto() {}

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
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
    return other instanceof TenantIdWithDefinitionsDto;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final TenantIdWithDefinitionsDto that = (TenantIdWithDefinitionsDto) o;
    return Objects.equals(id, that.id) && Objects.equals(definitions, that.definitions);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, definitions);
  }

  @Override
  public String toString() {
    return "TenantIdWithDefinitionsDto(id=" + getId() + ", definitions=" + getDefinitions() + ")";
  }
}
