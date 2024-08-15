/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.optimize.dto.optimize.datasource.DataSourceDto;
import java.io.Serializable;

public abstract class DefinitionOptimizeResponseDto implements Serializable, OptimizeDto {

  private String id;
  private String key;
  private String version;
  private String versionTag;
  private String name;
  private DataSourceDto dataSource;
  private String tenantId;
  private boolean deleted;
  @JsonIgnore private DefinitionType type;

  protected DefinitionOptimizeResponseDto(final String id, final DataSourceDto dataSource) {
    this.id = id;
    this.dataSource = dataSource;
  }

  public DefinitionOptimizeResponseDto(
      final String id,
      final String key,
      final String version,
      final String versionTag,
      final String name,
      final DataSourceDto dataSource,
      final String tenantId,
      final boolean deleted,
      final DefinitionType type) {
    this.id = id;
    this.key = key;
    this.version = version;
    this.versionTag = versionTag;
    this.name = name;
    this.dataSource = dataSource;
    this.tenantId = tenantId;
    this.deleted = deleted;
    this.type = type;
  }

  protected DefinitionOptimizeResponseDto() {}

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public String getKey() {
    return key;
  }

  public void setKey(final String key) {
    this.key = key;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(final String version) {
    this.version = version;
  }

  public String getVersionTag() {
    return versionTag;
  }

  public void setVersionTag(final String versionTag) {
    this.versionTag = versionTag;
  }

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public DataSourceDto getDataSource() {
    return dataSource;
  }

  public void setDataSource(final DataSourceDto dataSource) {
    this.dataSource = dataSource;
  }

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(final String tenantId) {
    this.tenantId = tenantId;
  }

  public boolean isDeleted() {
    return deleted;
  }

  public void setDeleted(final boolean deleted) {
    this.deleted = deleted;
  }

  public DefinitionType getType() {
    return type;
  }

  @JsonIgnore
  public void setType(final DefinitionType type) {
    this.type = type;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof DefinitionOptimizeResponseDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $id = getId();
    result = result * PRIME + ($id == null ? 43 : $id.hashCode());
    final Object $key = getKey();
    result = result * PRIME + ($key == null ? 43 : $key.hashCode());
    final Object $version = getVersion();
    result = result * PRIME + ($version == null ? 43 : $version.hashCode());
    final Object $versionTag = getVersionTag();
    result = result * PRIME + ($versionTag == null ? 43 : $versionTag.hashCode());
    final Object $name = getName();
    result = result * PRIME + ($name == null ? 43 : $name.hashCode());
    final Object $dataSource = getDataSource();
    result = result * PRIME + ($dataSource == null ? 43 : $dataSource.hashCode());
    final Object $tenantId = getTenantId();
    result = result * PRIME + ($tenantId == null ? 43 : $tenantId.hashCode());
    result = result * PRIME + (isDeleted() ? 79 : 97);
    final Object $type = getType();
    result = result * PRIME + ($type == null ? 43 : $type.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof DefinitionOptimizeResponseDto)) {
      return false;
    }
    final DefinitionOptimizeResponseDto other = (DefinitionOptimizeResponseDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$id = getId();
    final Object other$id = other.getId();
    if (this$id == null ? other$id != null : !this$id.equals(other$id)) {
      return false;
    }
    final Object this$key = getKey();
    final Object other$key = other.getKey();
    if (this$key == null ? other$key != null : !this$key.equals(other$key)) {
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
    final Object this$name = getName();
    final Object other$name = other.getName();
    if (this$name == null ? other$name != null : !this$name.equals(other$name)) {
      return false;
    }
    final Object this$dataSource = getDataSource();
    final Object other$dataSource = other.getDataSource();
    if (this$dataSource == null
        ? other$dataSource != null
        : !this$dataSource.equals(other$dataSource)) {
      return false;
    }
    final Object this$tenantId = getTenantId();
    final Object other$tenantId = other.getTenantId();
    if (this$tenantId == null ? other$tenantId != null : !this$tenantId.equals(other$tenantId)) {
      return false;
    }
    if (isDeleted() != other.isDeleted()) {
      return false;
    }
    final Object this$type = getType();
    final Object other$type = other.getType();
    if (this$type == null ? other$type != null : !this$type.equals(other$type)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "DefinitionOptimizeResponseDto(id="
        + getId()
        + ", key="
        + getKey()
        + ", version="
        + getVersion()
        + ", versionTag="
        + getVersionTag()
        + ", name="
        + getName()
        + ", dataSource="
        + getDataSource()
        + ", tenantId="
        + getTenantId()
        + ", deleted="
        + isDeleted()
        + ", type="
        + getType()
        + ")";
  }

  public static final class Fields {

    public static final String id = "id";
    public static final String key = "key";
    public static final String version = "version";
    public static final String versionTag = "versionTag";
    public static final String name = "name";
    public static final String dataSource = "dataSource";
    public static final String tenantId = "tenantId";
    public static final String deleted = "deleted";
    public static final String type = "type";
  }
}
