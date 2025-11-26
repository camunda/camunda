/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.clustervariable;

import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.webapps.schema.entities.SinceVersion;
import java.util.Objects;

public class ClusterVariableEntity implements ExporterEntity<ClusterVariableEntity> {

  @SinceVersion(value = "8.9.0", requireDefault = false)
  private String id;

  @SinceVersion(value = "8.9.0", requireDefault = false)
  private String name;

  @SinceVersion(value = "8.9.0", requireDefault = false)
  private String value;

  @SinceVersion(value = "8.9.0", requireDefault = false)
  private String fullValue;

  @SinceVersion(value = "8.9.0", requireDefault = false)
  private boolean isPreview;

  @SinceVersion(value = "8.9.0", requireDefault = false)
  private ClusterVariableScope scope;

  @SinceVersion(value = "8.9.0", requireDefault = false)
  private String tenantId;

  public boolean getIsPreview() {
    return isPreview;
  }

  public ClusterVariableEntity setIsPreview(final boolean preview) {
    isPreview = preview;
    return this;
  }

  public ClusterVariableScope getScope() {
    return scope;
  }

  public ClusterVariableEntity setScope(final ClusterVariableScope scope) {
    this.scope = scope;
    return this;
  }

  public String getFullValue() {
    return fullValue;
  }

  public ClusterVariableEntity setFullValue(final String fullValue) {
    this.fullValue = fullValue;
    return this;
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public ClusterVariableEntity setId(final String id) {
    this.id = id;
    return this;
  }

  public String getName() {
    return name;
  }

  public ClusterVariableEntity setName(final String name) {
    this.name = name;
    return this;
  }

  public String getValue() {
    return value;
  }

  public ClusterVariableEntity setValue(final String value) {
    this.value = value;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public ClusterVariableEntity setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name, value, fullValue, isPreview, scope, tenantId);
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ClusterVariableEntity that = (ClusterVariableEntity) o;
    return isPreview == that.isPreview
        && Objects.equals(id, that.id)
        && Objects.equals(name, that.name)
        && Objects.equals(value, that.value)
        && Objects.equals(fullValue, that.fullValue)
        && scope == that.scope
        && Objects.equals(tenantId, that.tenantId);
  }

  @Override
  public String toString() {
    return "ClusterVariableEntity{"
        + "id='"
        + id
        + '\''
        + ", name='"
        + name
        + '\''
        + ", value='"
        + value
        + '\''
        + ", fullValue='"
        + fullValue
        + '\''
        + ", isPreview="
        + isPreview
        + ", scope="
        + scope
        + ", tenantId='"
        + tenantId
        + '\''
        + '}';
  }
}
