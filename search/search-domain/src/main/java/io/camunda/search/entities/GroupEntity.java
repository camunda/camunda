/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GroupEntity(
    Long groupKey,
    String groupId,
    String name,
    @Nullable String description,
    @Nullable String externalId,
    @Nullable Map<String, Object> attributes,
    @Nullable OffsetDateTime createdAt,
    @Nullable OffsetDateTime lastModifiedAt) {

  public GroupEntity(
      final Long groupKey,
      final String groupId,
      final String name,
      final @Nullable String description,
      final @Nullable String externalId,
      final @Nullable Map<String, Object> attributes) {
    this(groupKey, groupId, name, description, externalId, attributes, null, null);
  }

  public GroupEntity(
      final Long groupKey,
      final String groupId,
      final String name,
      final @Nullable String description,
      final @Nullable String externalId) {
    this(groupKey, groupId, name, description, externalId, null, null, null);
  }

  public GroupEntity(
      final Long groupKey,
      final String groupId,
      final String name,
      final @Nullable String description) {
    this(groupKey, groupId, name, description, null, null, null, null);
  }

  public GroupEntity {
    Objects.requireNonNull(groupKey, "groupKey");
    Objects.requireNonNull(groupId, "groupId");
    Objects.requireNonNull(name, "name");
  }
}
