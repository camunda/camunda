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
public record DecisionRequirementsEntity(
    Long decisionRequirementsKey,
    String decisionRequirementsId,
    String name,
    Integer version,
    String resourceName,
    @Nullable String xml,
    String tenantId)
    implements TenantOwnedEntity {

  public DecisionRequirementsEntity {
    Objects.requireNonNull(decisionRequirementsKey, "decisionRequirementsKey");
    Objects.requireNonNull(decisionRequirementsId, "decisionRequirementsId");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(version, "version");
    Objects.requireNonNull(resourceName, "resourceName");
    Objects.requireNonNull(tenantId, "tenantId");
  }
}
