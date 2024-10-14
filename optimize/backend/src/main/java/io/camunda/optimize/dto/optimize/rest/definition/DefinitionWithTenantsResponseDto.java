/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest.definition;

import io.camunda.optimize.dto.optimize.rest.TenantResponseDto;
import java.util.List;

public class DefinitionWithTenantsResponseDto {

  private String key;
  private List<String> versions;
  private List<TenantResponseDto> tenants;

  public DefinitionWithTenantsResponseDto(
      final String key, final List<String> versions, final List<TenantResponseDto> tenants) {
    this.key = key;
    this.versions = versions;
    this.tenants = tenants;
  }

  protected DefinitionWithTenantsResponseDto() {}

  public String getKey() {
    return key;
  }

  public void setKey(final String key) {
    this.key = key;
  }

  public List<String> getVersions() {
    return versions;
  }

  public void setVersions(final List<String> versions) {
    this.versions = versions;
  }

  public List<TenantResponseDto> getTenants() {
    return tenants;
  }

  public void setTenants(final List<TenantResponseDto> tenants) {
    this.tenants = tenants;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof DefinitionWithTenantsResponseDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $key = getKey();
    result = result * PRIME + ($key == null ? 43 : $key.hashCode());
    final Object $versions = getVersions();
    result = result * PRIME + ($versions == null ? 43 : $versions.hashCode());
    final Object $tenants = getTenants();
    result = result * PRIME + ($tenants == null ? 43 : $tenants.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof DefinitionWithTenantsResponseDto)) {
      return false;
    }
    final DefinitionWithTenantsResponseDto other = (DefinitionWithTenantsResponseDto) o;
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
    final Object this$tenants = getTenants();
    final Object other$tenants = other.getTenants();
    if (this$tenants == null ? other$tenants != null : !this$tenants.equals(other$tenants)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "DefinitionWithTenantsResponseDto(key="
        + getKey()
        + ", versions="
        + getVersions()
        + ", tenants="
        + getTenants()
        + ")";
  }
}
