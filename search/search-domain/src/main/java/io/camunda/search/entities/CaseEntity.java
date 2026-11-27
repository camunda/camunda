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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CaseEntity(
    Long caseKey,
    Long processInstanceKey,
    String processDefinitionId,
    String tenantId,
    @Nullable String businessId,
    @Nullable String title,
    CaseState state,
    List<CaseStageEntity> stages)
    implements TenantOwnedEntity {

  public CaseEntity {
    Objects.requireNonNull(caseKey, "caseKey");
    Objects.requireNonNull(processInstanceKey, "processInstanceKey");
    Objects.requireNonNull(processDefinitionId, "processDefinitionId");
    Objects.requireNonNull(tenantId, "tenantId");
    Objects.requireNonNull(state, "state");
    // Mutable list required: MyBatis hydrates by calling .add()
    stages = stages != null ? new ArrayList<>(stages) : new ArrayList<>();
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record CaseStageEntity(
      String elementId, String name, boolean alternative, CaseStageStatus status) {
    public CaseStageEntity {
      Objects.requireNonNull(elementId, "elementId");
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(status, "status");
    }
  }

  public enum CaseState {
    ACTIVE,
    SUSPENDED,
    COMPLETED,
    TERMINATED
  }

  public enum CaseStageStatus {
    NOT_STARTED,
    ACTIVE,
    COMPLETED,
    TERMINATED
  }
}
