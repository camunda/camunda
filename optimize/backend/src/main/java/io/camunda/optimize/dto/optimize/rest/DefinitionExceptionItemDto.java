/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest;

import io.camunda.optimize.dto.optimize.DefinitionType;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;

public class DefinitionExceptionItemDto implements Serializable {

  private DefinitionType type;
  private String key;
  private List<String> versions;
  private List<String> tenantIds;

  public DefinitionExceptionItemDto(
      final DefinitionType type,
      final String key,
      final List<String> versions,
      final List<String> tenantIds) {
    this.type = type;
    this.key = key;
    this.versions = versions;
    this.tenantIds = tenantIds;
  }

  public DefinitionExceptionItemDto() {}

  public DefinitionType getType() {
    return type;
  }

  public String getKey() {
    return key;
  }

  public List<String> getVersions() {
    return versions;
  }

  public List<String> getTenantIds() {
    return tenantIds;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof DefinitionExceptionItemDto;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DefinitionExceptionItemDto that = (DefinitionExceptionItemDto) o;
    return type == that.type
        && Objects.equals(key, that.key)
        && Objects.equals(versions, that.versions)
        && Objects.equals(tenantIds, that.tenantIds);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, key, versions, tenantIds);
  }

  public static DefinitionExceptionItemDtoBuilder builder() {
    return new DefinitionExceptionItemDtoBuilder();
  }

  public static class DefinitionExceptionItemDtoBuilder {

    private DefinitionType type;
    private String key;
    private List<String> versions;
    private List<String> tenantIds;

    DefinitionExceptionItemDtoBuilder() {}

    public DefinitionExceptionItemDtoBuilder type(final DefinitionType type) {
      this.type = type;
      return this;
    }

    public DefinitionExceptionItemDtoBuilder key(final String key) {
      this.key = key;
      return this;
    }

    public DefinitionExceptionItemDtoBuilder versions(final List<String> versions) {
      this.versions = versions;
      return this;
    }

    public DefinitionExceptionItemDtoBuilder tenantIds(final List<String> tenantIds) {
      this.tenantIds = tenantIds;
      return this;
    }

    public DefinitionExceptionItemDto build() {
      return new DefinitionExceptionItemDto(type, key, versions, tenantIds);
    }

    @Override
    public String toString() {
      return "DefinitionExceptionItemDto.DefinitionExceptionItemDtoBuilder(type="
          + type
          + ", key="
          + key
          + ", versions="
          + versions
          + ", tenantIds="
          + tenantIds
          + ")";
    }
  }
}
