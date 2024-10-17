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
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $id = getId();
    result = result * PRIME + ($id == null ? 43 : $id.hashCode());
    final Object $definitions = getDefinitions();
    result = result * PRIME + ($definitions == null ? 43 : $definitions.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof TenantIdWithDefinitionsDto)) {
      return false;
    }
    final TenantIdWithDefinitionsDto other = (TenantIdWithDefinitionsDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$id = getId();
    final Object other$id = other.getId();
    if (this$id == null ? other$id != null : !this$id.equals(other$id)) {
      return false;
    }
    final Object this$definitions = getDefinitions();
    final Object other$definitions = other.getDefinitions();
    if (this$definitions == null
        ? other$definitions != null
        : !this$definitions.equals(other$definitions)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "TenantIdWithDefinitionsDto(id=" + getId() + ", definitions=" + getDefinitions() + ")";
  }
}
