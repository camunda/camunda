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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
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
      return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public boolean equals(final Object o) {
      return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
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
