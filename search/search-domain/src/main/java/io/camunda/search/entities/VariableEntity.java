/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.security.core.authz.TenantOwnedEntity;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
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
    // absent on variables imported by Operate 8.1 and earlier.
    @Nullable String processDefinitionId,
    String tenantId,
    // empty when the variable is not protected.
    @Nullable Set<ProtectionMode> protectionModes)
    implements TenantOwnedEntity {

  public VariableEntity(
      final Long variableKey,
      final String name,
      final String value,
      final @Nullable String fullValue,
      final Boolean isPreview,
      final Long scopeKey,
      final Long processInstanceKey,
      final @Nullable Long rootProcessInstanceKey,
      final @Nullable String processDefinitionId,
      final String tenantId) {
    this(
        variableKey,
        name,
        value,
        fullValue,
        isPreview,
        scopeKey,
        processInstanceKey,
        rootProcessInstanceKey,
        processDefinitionId,
        tenantId,
        new HashSet<>());
  }

  public VariableEntity {
    Objects.requireNonNull(variableKey, "variableKey");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(value, "value");
    Objects.requireNonNull(isPreview, "isPreview");
    Objects.requireNonNull(scopeKey, "scopeKey");
    Objects.requireNonNull(processInstanceKey, "processInstanceKey");
    Objects.requireNonNull(tenantId, "tenantId");
    protectionModes = protectionModes == null ? new HashSet<>() : protectionModes;
  }

  public enum ProtectionMode {
    REDACT
  }
}
