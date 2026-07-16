/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ClusterVariableEntity(
    String id,
    String name,
    String value,
    @Nullable String fullValue,
    @Nullable Boolean isPreview,
    ClusterVariableScope scope,
    @Nullable String tenantId,
    @Nullable List<MetadataEntry> metadata,
    @Nullable ClusterVariableKind kind)
    implements TenantOwnedEntity {

  public ClusterVariableEntity {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(value, "value");
    Objects.requireNonNull(scope, "scope");
    metadata = metadata != null ? new ArrayList<>(metadata) : new ArrayList<>();
  }

  // Used by the RDBMS resultMap's <constructor>, whose args mirror this signature; the
  // <collection> for metadata is mapped separately and hydrated onto the mutable list below.
  public ClusterVariableEntity(
      final String id,
      final String name,
      final String value,
      final String fullValue,
      final Boolean isPreview,
      final ClusterVariableScope scope,
      final String tenantId) {
    this(id, name, value, fullValue, isPreview, scope, tenantId, new ArrayList<>());
  }

  @Override
  public boolean hasTenantScope() {
    return ClusterVariableScope.TENANT.equals(scope);
  }

  public record MetadataEntry(String key, @Nullable String value, @Nullable Double valueNumber) {
    public MetadataEntry {
      Objects.requireNonNull(key, "key");
    }
  }
}
