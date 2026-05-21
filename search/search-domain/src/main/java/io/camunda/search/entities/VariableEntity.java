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
    String name,
    String value,
    // only set when the value exceeds the preview size threshold.
    @Nullable String fullValue,
    Boolean isPreview,
    Long scopeKey,
    Long processInstanceKey,
    // ES/OS handler only writes when rootProcessInstanceKey > 0 (absent for top-level instances).
    @Nullable Long rootProcessInstanceKey,
    String processDefinitionId,
    String tenantId)
    implements TenantOwnedEntity {

  public VariableEntity {
    Objects.requireNonNull(variableKey, "variableKey");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(value, "value");
    Objects.requireNonNull(isPreview, "isPreview");
    Objects.requireNonNull(scopeKey, "scopeKey");
    Objects.requireNonNull(processInstanceKey, "processInstanceKey");
    Objects.requireNonNull(processDefinitionId, "processDefinitionId");
    Objects.requireNonNull(tenantId, "tenantId");
  }
}
