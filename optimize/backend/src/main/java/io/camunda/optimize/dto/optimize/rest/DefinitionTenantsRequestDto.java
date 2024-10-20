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
  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public String toString() {
    return "DefinitionTenantsRequestDto(versions="
        + getVersions()
        + ", filterByCollectionScope="
        + getFilterByCollectionScope()
        + ")";
  }

  private static List<String> $default$versions() {
    return new ArrayList<>();
  }

  public static DefinitionTenantsRequestDtoBuilder builder() {
    return new DefinitionTenantsRequestDtoBuilder();
  }

  public static class DefinitionTenantsRequestDtoBuilder {

    private List<String> versions$value;
    private boolean versions$set;
    private String filterByCollectionScope;

    DefinitionTenantsRequestDtoBuilder() {}

    public DefinitionTenantsRequestDtoBuilder versions(final List<String> versions) {
      versions$value = versions;
      versions$set = true;
      return this;
    }

    public DefinitionTenantsRequestDtoBuilder filterByCollectionScope(
        final String filterByCollectionScope) {
      this.filterByCollectionScope = filterByCollectionScope;
      return this;
    }

    public DefinitionTenantsRequestDto build() {
      List<String> versions$value = this.versions$value;
      if (!versions$set) {
        versions$value = DefinitionTenantsRequestDto.$default$versions();
      }
      return new DefinitionTenantsRequestDto(versions$value, filterByCollectionScope);
    }

    @Override
    public String toString() {
      return "DefinitionTenantsRequestDto.DefinitionTenantsRequestDtoBuilder(versions$value="
          + versions$value
          + ", filterByCollectionScope="
          + filterByCollectionScope
          + ")";
    }
  }
}
