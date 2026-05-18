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
import java.util.Objects;

/**
 * Base DTO for process and decision definitions as stored in Optimize's ES/OS index.
 *
 * <p><b>Terminology note:</b> Optimize retains C7 identifier conventions, which are the
 * <em>inverse</em> of C8 / Zeebe conventions. The table below shows how Optimize field names map to
 * their C8 equivalents:
 *
 * <table border="1">
 *   <tr><th>Optimize field name</th><th>Zeebe field name</th><th>Meaning</th></tr>
 *   <tr><td>{@link #id}</td><td>{@code processDefinitionKey}</td>
 *       <td>Unique {@code Long} key for a specific definition version (stored as {@code String})</td></tr>
 *   <tr><td>{@link #key}</td><td>{@code bpmnProcessId}</td>
 *       <td>Non-unique BPMN process ID string, e.g. {@code "invoice-process"}</td></tr>
 * </table>
 *
 * <p>This naming is intentional and must not be changed. See {@code
 * optimize/docs/adr/001-c7-naming-conventions.md} for the full rationale.
 */
public abstract class DefinitionOptimizeResponseDto implements Serializable, OptimizeDto {

  /**
   * Maps to Zeebe's {@code processDefinitionKey}. See class-level Javadoc for the naming rationale.
   */
  private String id;

  /** Maps to Zeebe's {@code bpmnProcessId}. See class-level Javadoc. */
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
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DefinitionOptimizeResponseDto that = (DefinitionOptimizeResponseDto) o;
    return deleted == that.deleted
        && Objects.equals(id, that.id)
        && Objects.equals(key, that.key)
        && Objects.equals(version, that.version)
        && Objects.equals(versionTag, that.versionTag)
        && Objects.equals(name, that.name)
        && Objects.equals(dataSource, that.dataSource)
        && Objects.equals(tenantId, that.tenantId)
        && type == that.type;
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, key, version, versionTag, name, dataSource, tenantId, deleted, type);
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

  @SuppressWarnings("checkstyle:ConstantName")
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
