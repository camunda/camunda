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
import java.util.Objects;

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
    return Objects.hash(key, versions, tenants);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DefinitionWithTenantsResponseDto that = (DefinitionWithTenantsResponseDto) o;
    return Objects.equals(key, that.key)
        && Objects.equals(versions, that.versions)
        && Objects.equals(tenants, that.tenants);
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
