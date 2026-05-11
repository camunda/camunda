/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.entities;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record ProcessDefinitionEntity(
    Long processDefinitionKey,
    @Nullable String name,
    String processDefinitionId,
    @Nullable String bpmnXml,
    String resourceName,
    Integer version,
    @Nullable String versionTag,
    String tenantId,
    @Nullable String formId)
    implements TenantOwnedEntity {

  public ProcessDefinitionEntity {
    Objects.requireNonNull(processDefinitionKey, "processDefinitionKey");
    Objects.requireNonNull(processDefinitionId, "processDefinitionId");
    Objects.requireNonNull(resourceName, "resourceName");
    Objects.requireNonNull(version, "version");
    Objects.requireNonNull(tenantId, "tenantId");
  }
}
