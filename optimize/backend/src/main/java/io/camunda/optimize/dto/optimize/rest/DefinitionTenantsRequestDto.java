/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class DefinitionTenantsRequestDto {

  private List<String> versions = new ArrayList<>();

  private String filterByCollectionScope;

  public DefinitionTenantsRequestDto(
      final List<String> versions, final String filterByCollectionScope) {
    this.versions = versions;
    this.filterByCollectionScope = filterByCollectionScope;
  }

  protected DefinitionTenantsRequestDto() {}

  public List<String> getVersions() {
    return versions;
  }

  public void setVersions(final List<String> versions) {
    this.versions = versions;
  }

  public String getFilterByCollectionScope() {
    return filterByCollectionScope;
  }

  public void setFilterByCollectionScope(final String filterByCollectionScope) {
    this.filterByCollectionScope = filterByCollectionScope;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof DefinitionTenantsRequestDto;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DefinitionTenantsRequestDto that = (DefinitionTenantsRequestDto) o;
    return Objects.equals(versions, that.versions)
        && Objects.equals(filterByCollectionScope, that.filterByCollectionScope);
  }

  @Override
  public int hashCode() {
    return Objects.hash(versions, filterByCollectionScope);
  }

  @Override
  public String toString() {
    return "DefinitionTenantsRequestDto(versions="
        + getVersions()
        + ", filterByCollectionScope="
        + getFilterByCollectionScope()
        + ")";
  }

  private static List<String> defaultVersions() {
    return new ArrayList<>();
  }

  public static DefinitionTenantsRequestDtoBuilder builder() {
    return new DefinitionTenantsRequestDtoBuilder();
  }

  public static class DefinitionTenantsRequestDtoBuilder {

    private List<String> versionsValue;
    private boolean versionsSet;
    private String filterByCollectionScope;

    DefinitionTenantsRequestDtoBuilder() {}

    public DefinitionTenantsRequestDtoBuilder versions(final List<String> versions) {
      versionsValue = versions;
      versionsSet = true;
      return this;
    }

    public DefinitionTenantsRequestDtoBuilder filterByCollectionScope(
        final String filterByCollectionScope) {
      this.filterByCollectionScope = filterByCollectionScope;
      return this;
    }

    public DefinitionTenantsRequestDto build() {
      List<String> versionsValue = this.versionsValue;
      if (!versionsSet) {
        versionsValue = DefinitionTenantsRequestDto.defaultVersions();
      }
      return new DefinitionTenantsRequestDto(versionsValue, filterByCollectionScope);
    }

    @Override
    public String toString() {
      return "DefinitionTenantsRequestDto.DefinitionTenantsRequestDtoBuilder(versionsValue="
          + versionsValue
          + ", filterByCollectionScope="
          + filterByCollectionScope
          + ")";
    }
  }
}
