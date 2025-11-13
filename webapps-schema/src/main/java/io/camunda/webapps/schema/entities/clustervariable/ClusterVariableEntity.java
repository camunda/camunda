/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.clustervariable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.webapps.schema.entities.ExporterEntity;
import java.util.Arrays;
import java.util.Objects;

public class ClusterVariableEntity implements ExporterEntity<ClusterVariableEntity> {

  private String clusterVariableId;
  private String name;
  private String value;
  private String fullValue;
  private boolean isPreview;
  private String scope;
  private String resourceId;
  @JsonIgnore private Object[] sortValues;

  public boolean isPreview() {
    return isPreview;
  }

  public ClusterVariableEntity setPreview(final boolean preview) {
    isPreview = preview;
    return this;
  }

  public String getScope() {
    return scope;
  }

  public ClusterVariableEntity setScope(final String scope) {
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
    return clusterVariableId;
  }

  @Override
  public ClusterVariableEntity setId(final String id) {
    clusterVariableId = id;
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

  @Override
  public int hashCode() {
    return Objects.hash(
        clusterVariableId,
        name,
        value,
        fullValue,
        isPreview,
        scope,
        resourceId,
        Arrays.hashCode(sortValues));
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ClusterVariableEntity that = (ClusterVariableEntity) o;
    return isPreview == that.isPreview
        && Objects.equals(clusterVariableId, that.clusterVariableId)
        && Objects.equals(name, that.name)
        && Objects.equals(value, that.value)
        && Objects.equals(fullValue, that.fullValue)
        && Objects.equals(scope, that.scope)
        && Objects.equals(resourceId, that.resourceId)
        && Objects.deepEquals(sortValues, that.sortValues);
  }

  @Override
  public String toString() {
    return "ClusterVariableEntity{"
        + "clusterVariableId='"
        + clusterVariableId
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
        + ", scope='"
        + scope
        + '\''
        + ", resourceId='"
        + resourceId
        + '\''
        + ", sortValues="
        + Arrays.toString(sortValues)
        + '}';
  }

  public Object[] getSortValues() {
    return sortValues;
  }

  public ClusterVariableEntity setSortValues(final Object[] sortValues) {
    this.sortValues = sortValues;
    return this;
  }

  public String getResourceId() {
    return resourceId;
  }

  public ClusterVariableEntity setResourceId(final String resourceId) {
    this.resourceId = resourceId;
    return this;
  }
}
