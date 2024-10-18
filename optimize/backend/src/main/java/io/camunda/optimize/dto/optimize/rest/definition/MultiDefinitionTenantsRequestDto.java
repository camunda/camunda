/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest.definition;

import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

public class MultiDefinitionTenantsRequestDto {

  private List<DefinitionDto> definitions = new ArrayList<>();
  private String filterByCollectionScope;

  public MultiDefinitionTenantsRequestDto(final List<DefinitionDto> definitionDtos) {
    definitions = definitionDtos;
  }

  public MultiDefinitionTenantsRequestDto(
      final List<DefinitionDto> definitions, final String filterByCollectionScope) {
    this.definitions = definitions;
    this.filterByCollectionScope = filterByCollectionScope;
  }

  public MultiDefinitionTenantsRequestDto() {}

  public List<DefinitionDto> getDefinitions() {
    return definitions;
  }

  public void setDefinitions(final List<DefinitionDto> definitions) {
    this.definitions = definitions;
  }

  public String getFilterByCollectionScope() {
    return filterByCollectionScope;
  }

  public void setFilterByCollectionScope(final String filterByCollectionScope) {
    this.filterByCollectionScope = filterByCollectionScope;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof MultiDefinitionTenantsRequestDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $definitions = getDefinitions();
    result = result * PRIME + ($definitions == null ? 43 : $definitions.hashCode());
    final Object $filterByCollectionScope = getFilterByCollectionScope();
    result =
        result * PRIME
            + ($filterByCollectionScope == null ? 43 : $filterByCollectionScope.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof MultiDefinitionTenantsRequestDto)) {
      return false;
    }
    final MultiDefinitionTenantsRequestDto other = (MultiDefinitionTenantsRequestDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$definitions = getDefinitions();
    final Object other$definitions = other.getDefinitions();
    if (this$definitions == null
        ? other$definitions != null
        : !this$definitions.equals(other$definitions)) {
      return false;
    }
    final Object this$filterByCollectionScope = getFilterByCollectionScope();
    final Object other$filterByCollectionScope = other.getFilterByCollectionScope();
    if (this$filterByCollectionScope == null
        ? other$filterByCollectionScope != null
        : !this$filterByCollectionScope.equals(other$filterByCollectionScope)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "MultiDefinitionTenantsRequestDto(definitions="
        + getDefinitions()
        + ", filterByCollectionScope="
        + getFilterByCollectionScope()
        + ")";
  }

  public static class DefinitionDto {

    @NotNull private String key;
    @NotNull private List<String> versions = new ArrayList<>();

    public DefinitionDto(@NotNull final String key, @NotNull final List<String> versions) {
      this.key = key;
      this.versions = versions;
    }

    public DefinitionDto() {}

    public @NotNull String getKey() {
      return key;
    }

    public void setKey(@NotNull final String key) {
      this.key = key;
    }

    public @NotNull List<String> getVersions() {
      return versions;
    }

    public void setVersions(@NotNull final List<String> versions) {
      this.versions = versions;
    }

    protected boolean canEqual(final Object other) {
      return other instanceof DefinitionDto;
    }

    @Override
    public int hashCode() {
      final int PRIME = 59;
      int result = 1;
      final Object $key = getKey();
      result = result * PRIME + ($key == null ? 43 : $key.hashCode());
      final Object $versions = getVersions();
      result = result * PRIME + ($versions == null ? 43 : $versions.hashCode());
      return result;
    }

    @Override
    public boolean equals(final Object o) {
      if (o == this) {
        return true;
      }
      if (!(o instanceof DefinitionDto)) {
        return false;
      }
      final DefinitionDto other = (DefinitionDto) o;
      if (!other.canEqual((Object) this)) {
        return false;
      }
      final Object this$key = getKey();
      final Object other$key = other.getKey();
      if (this$key == null ? other$key != null : !this$key.equals(other$key)) {
        return false;
      }
      final Object this$versions = getVersions();
      final Object other$versions = other.getVersions();
      if (this$versions == null ? other$versions != null : !this$versions.equals(other$versions)) {
        return false;
      }
      return true;
    }

    @Override
    public String toString() {
      return "MultiDefinitionTenantsRequestDto.DefinitionDto(key="
          + getKey()
          + ", versions="
          + getVersions()
          + ")";
    }
  }
}
