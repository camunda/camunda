/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single;

import static io.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.optimize.dto.optimize.ReportConstants;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

public class ReportDataDefinitionDto {

  @NotEmpty private String identifier = UUID.randomUUID().toString();
  private String key;
  private String name;
  private String displayName;
  private List<String> versions = List.of(ALL_VERSIONS);
  private List<String> tenantIds = ReportConstants.DEFAULT_TENANT_IDS;

  public ReportDataDefinitionDto(final String key) {
    this.key = key;
  }

  public ReportDataDefinitionDto(final String key, final List<String> tenantIds) {
    this.key = key;
    this.tenantIds = tenantIds;
  }

  public ReportDataDefinitionDto(
      final String key,
      final String name,
      final List<String> versions,
      final List<String> tenantIds) {
    this.key = key;
    this.name = name;
    this.versions = versions;
    this.tenantIds = tenantIds;
  }

  public ReportDataDefinitionDto(
      final String key,
      final String name,
      final List<String> versions,
      final List<String> tenantIds,
      final String displayName) {
    this.key = key;
    this.name = name;
    this.versions = versions;
    this.tenantIds = tenantIds;
    this.displayName = displayName;
  }

  public ReportDataDefinitionDto(
      final String key, final List<String> versions, final List<String> tenantIds) {
    this.key = key;
    this.versions = versions;
    this.tenantIds = tenantIds;
  }

  public ReportDataDefinitionDto(final String identifier, final String key) {
    this.identifier = identifier;
    this.key = key;
  }

  public ReportDataDefinitionDto(
      final String identifier, final String key, final String displayName) {
    this.identifier = identifier;
    this.key = key;
    this.displayName = displayName;
  }

  public ReportDataDefinitionDto(
      final String identifier, final String key, final List<String> versions) {
    this.identifier = identifier;
    this.key = key;
    this.versions = versions;
  }

  public ReportDataDefinitionDto(
      @NotEmpty final String identifier,
      final String key,
      final String name,
      final String displayName,
      final List<String> versions,
      final List<String> tenantIds) {
    this.identifier = identifier;
    this.key = key;
    this.name = name;
    this.displayName = displayName;
    this.versions = versions;
    this.tenantIds = tenantIds;
  }

  public ReportDataDefinitionDto() {}

  @JsonIgnore
  public void setVersion(final String version) {
    versions = List.of(version);
  }

  public @NotEmpty String getIdentifier() {
    return identifier;
  }

  public void setIdentifier(@NotEmpty final String identifier) {
    this.identifier = identifier;
  }

  public String getKey() {
    return key;
  }

  public void setKey(final String key) {
    this.key = key;
  }

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(final String displayName) {
    this.displayName = displayName;
  }

  public List<String> getVersions() {
    return versions;
  }

  public void setVersions(final List<String> versions) {
    this.versions = versions;
  }

  public List<String> getTenantIds() {
    return tenantIds;
  }

  public void setTenantIds(final List<String> tenantIds) {
    this.tenantIds = tenantIds;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ReportDataDefinitionDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $identifier = getIdentifier();
    result = result * PRIME + ($identifier == null ? 43 : $identifier.hashCode());
    final Object $key = getKey();
    result = result * PRIME + ($key == null ? 43 : $key.hashCode());
    final Object $name = getName();
    result = result * PRIME + ($name == null ? 43 : $name.hashCode());
    final Object $displayName = getDisplayName();
    result = result * PRIME + ($displayName == null ? 43 : $displayName.hashCode());
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
    if (!(o instanceof ReportDataDefinitionDto)) {
      return false;
    }
    final ReportDataDefinitionDto other = (ReportDataDefinitionDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$identifier = getIdentifier();
    final Object other$identifier = other.getIdentifier();
    if (this$identifier == null
        ? other$identifier != null
        : !this$identifier.equals(other$identifier)) {
      return false;
    }
    final Object this$key = getKey();
    final Object other$key = other.getKey();
    if (this$key == null ? other$key != null : !this$key.equals(other$key)) {
      return false;
    }
    final Object this$name = getName();
    final Object other$name = other.getName();
    if (this$name == null ? other$name != null : !this$name.equals(other$name)) {
      return false;
    }
    final Object this$displayName = getDisplayName();
    final Object other$displayName = other.getDisplayName();
    if (this$displayName == null
        ? other$displayName != null
        : !this$displayName.equals(other$displayName)) {
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

  @Override
  public String toString() {
    return "ReportDataDefinitionDto(identifier="
        + getIdentifier()
        + ", key="
        + getKey()
        + ", name="
        + getName()
        + ", displayName="
        + getDisplayName()
        + ", versions="
        + getVersions()
        + ", tenantIds="
        + getTenantIds()
        + ")";
  }

  public static final class Fields {

    public static final String identifier = "identifier";
    public static final String key = "key";
    public static final String name = "name";
    public static final String displayName = "displayName";
    public static final String versions = "versions";
    public static final String tenantIds = "tenantIds";
  }
}
