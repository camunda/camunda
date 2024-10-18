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
  public int hashCode() {
    final int PRIME = 59;
    int result = super.hashCode();
    final Object $version = getVersion();
    result = result * PRIME + ($version == null ? 43 : $version.hashCode());
    final Object $versionTag = getVersionTag();
    result = result * PRIME + ($versionTag == null ? 43 : $versionTag.hashCode());
    final Object $tenants = getTenants();
    result = result * PRIME + ($tenants == null ? 43 : $tenants.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof DefinitionVersionWithTenantsDto)) {
      return false;
    }
    final DefinitionVersionWithTenantsDto other = (DefinitionVersionWithTenantsDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final Object this$version = getVersion();
    final Object other$version = other.getVersion();
    if (this$version == null ? other$version != null : !this$version.equals(other$version)) {
      return false;
    }
    final Object this$versionTag = getVersionTag();
    final Object other$versionTag = other.getVersionTag();
    if (this$versionTag == null
        ? other$versionTag != null
        : !this$versionTag.equals(other$versionTag)) {
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
    return "DefinitionVersionWithTenantsDto(version="
        + getVersion()
        + ", versionTag="
        + getVersionTag()
        + ", tenants="
        + getTenants()
        + ")";
  }
}
