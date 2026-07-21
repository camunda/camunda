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
import java.util.Objects;
import org.jspecify.annotations.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DecisionDefinitionEntity(
    Long decisionDefinitionKey,
    String decisionDefinitionId,
    String name,
    Integer version,
    String decisionRequirementsId,
    Long decisionRequirementsKey,
    @Nullable String decisionRequirementsName,
    Integer decisionRequirementsVersion,
    String tenantId,
    @Nullable String updatedBy,
    @Nullable OffsetDateTime updatedAt)
    implements TenantOwnedEntity {

  public DecisionDefinitionEntity {
    Objects.requireNonNull(decisionDefinitionKey, "decisionDefinitionKey");
    Objects.requireNonNull(decisionDefinitionId, "decisionDefinitionId");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(version, "version");
    Objects.requireNonNull(decisionRequirementsId, "decisionRequirementsId");
    Objects.requireNonNull(decisionRequirementsKey, "decisionRequirementsKey");
    Objects.requireNonNull(decisionRequirementsVersion, "decisionRequirementsVersion");
    Objects.requireNonNull(tenantId, "tenantId");
  }

  public DecisionDefinitionEntity(
      final Long decisionDefinitionKey,
      final String decisionDefinitionId,
      final String name,
      final Integer version,
      final String decisionRequirementsId,
      final Long decisionRequirementsKey,
      final @Nullable String decisionRequirementsName,
      final Integer decisionRequirementsVersion,
      final String tenantId) {
    this(
        decisionDefinitionKey,
        decisionDefinitionId,
        name,
        version,
        decisionRequirementsId,
        decisionRequirementsKey,
        decisionRequirementsName,
        decisionRequirementsVersion,
        tenantId,
        null,
        null);
  }

  public DecisionDefinitionEntity withUpdateMetadata(
      final @Nullable String newUpdatedBy, final @Nullable OffsetDateTime newUpdatedAt) {
    return new DecisionDefinitionEntity(
        decisionDefinitionKey,
        decisionDefinitionId,
        name,
        version,
        decisionRequirementsId,
        decisionRequirementsKey,
        decisionRequirementsName,
        decisionRequirementsVersion,
        tenantId,
        newUpdatedBy,
        newUpdatedAt);
  }
}
