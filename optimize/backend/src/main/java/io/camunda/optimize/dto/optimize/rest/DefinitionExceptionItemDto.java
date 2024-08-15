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
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $type = getType();
    result = result * PRIME + ($type == null ? 43 : $type.hashCode());
    final Object $key = getKey();
    result = result * PRIME + ($key == null ? 43 : $key.hashCode());
    final Object $versions = getVersions();
    result = result * PRIME + ($versions == null ? 43 : $versions.hashCode());
    final Object $tenantIds = getTenantIds();
    result = result * PRIME + ($tenantIds == null ? 43 : $tenantIds.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof DefinitionExceptionItemDto)) {
      return false;
    }
    final DefinitionExceptionItemDto other = (DefinitionExceptionItemDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$type = getType();
    final Object other$type = other.getType();
    if (this$type == null ? other$type != null : !this$type.equals(other$type)) {
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
    final Object this$tenantIds = getTenantIds();
    final Object other$tenantIds = other.getTenantIds();
    if (this$tenantIds == null
        ? other$tenantIds != null
        : !this$tenantIds.equals(other$tenantIds)) {
      return false;
    }
    return true;
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
