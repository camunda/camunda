/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.definition;

import static java.util.Comparator.naturalOrder;

import io.camunda.optimize.dto.optimize.SimpleDefinitionDto;
import io.camunda.optimize.dto.optimize.TenantDto;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class DefinitionVersionWithTenantsDto extends SimpleDefinitionDto {

  private String version;
  private String versionTag;
  private List<TenantDto> tenants;

  public DefinitionVersionWithTenantsDto(
      final String version, final String versionTag, final List<TenantDto> tenants) {
    if (version == null) {
      throw new IllegalArgumentException("version cannot be null");
    }

    if (tenants == null) {
      throw new IllegalArgumentException("tenants cannot be null");
    }

    this.version = version;
    this.versionTag = versionTag;
    this.tenants = tenants;
  }

  protected DefinitionVersionWithTenantsDto() {}

  public void sort() {
    tenants.sort(Comparator.comparing(TenantDto::getId, Comparator.nullsFirst(naturalOrder())));
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(final String version) {
    if (version == null) {
      throw new IllegalArgumentException("version cannot be null");
    }

    this.version = version;
  }

  public String getVersionTag() {
    return versionTag;
  }

  public void setVersionTag(final String versionTag) {
    this.versionTag = versionTag;
  }

  public List<TenantDto> getTenants() {
    return tenants;
  }

  public void setTenants(final List<TenantDto> tenants) {
    if (tenants == null) {
      throw new IllegalArgumentException("tenants cannot be null");
    }

    this.tenants = tenants;
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof DefinitionVersionWithTenantsDto;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final DefinitionVersionWithTenantsDto that = (DefinitionVersionWithTenantsDto) o;
    return Objects.equals(version, that.version)
        && Objects.equals(versionTag, that.versionTag)
        && Objects.equals(tenants, that.tenants);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), version, versionTag, tenants);
  }

  @Override
  public String toString() {
    return "DefinitionVersionWithTenantsDto(version="
        + getVersion()
        + ", versionTag="
        + getVersionTag()
        + ", tenants="
        + getTenants()
        + ")";
  }
}
