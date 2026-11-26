/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.entities;

import java.time.OffsetDateTime;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record CaseEntity(
    Long caseKey,
    Long processInstanceKey,
    Long processDefinitionKey,
    String processDefinitionId,
    String tenantId,
    @Nullable String businessId,
    @Nullable String title,
    CaseState state,
    OffsetDateTime startDate,
    @Nullable OffsetDateTime endDate,
    List<String> startElementIds,
    List<CaseStageEntity> stages) {

  public record CaseStageEntity(
      String elementId,
      @Nullable String name,
      boolean alternative,
      CaseStageState state,
      List<String> nextElementIds) {}

  public enum CaseState {
    ACTIVE,
    COMPLETED,
    TERMINATED
  }

  public enum CaseStageState {
    NOT_STARTED,
    ACTIVE,
    COMPLETED,
    TERMINATED
  }
}
