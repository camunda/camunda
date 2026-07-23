/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.entities;

public record ProcessDefinitionEntity(
    Long processDefinitionKey,
    String name,
    String processDefinitionId,
    String bpmnXml,
    String resourceName,
    Integer version,
    String versionTag,
    String tenantId,
<<<<<<< HEAD
    String formId)
    implements TenantOwnedEntity {}
=======
    @Nullable String formId,
    @Nullable ProcessDefinitionState state)
    implements TenantOwnedEntity {

  public ProcessDefinitionEntity {
    Objects.requireNonNull(processDefinitionKey, "processDefinitionKey");
    Objects.requireNonNull(processDefinitionId, "processDefinitionId");
    Objects.requireNonNull(resourceName, "resourceName");
    Objects.requireNonNull(version, "version");
    Objects.requireNonNull(tenantId, "tenantId");
  }

  public enum ProcessDefinitionState {
    ACTIVE,
    DELETED
  }
}
>>>>>>> 7af48743 (refactor: replace isDeleted with a state enum in the search domain)
