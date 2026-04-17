/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VariableEntity(
    Long variableKey,
    @Nullable String name,
    @Nullable String value,
    @Nullable String fullValue,
    @Nullable Boolean isPreview,
    @Nullable Long scopeKey,
    @Nullable Long processInstanceKey,
    @Nullable Long rootProcessInstanceKey,
    @Nullable String processDefinitionId,
    String tenantId)
    implements TenantOwnedEntity {

  public VariableEntity {
    Objects.requireNonNull(variableKey, "variableKey");
    Objects.requireNonNull(tenantId, "tenantId");
  }
}
