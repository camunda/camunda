/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.entity;

import io.camunda.search.clients.transformers.ServiceTransformer;
import io.camunda.search.entities.CaseEntity;
import io.camunda.search.entities.CaseEntity.CaseStageEntity;
import io.camunda.search.entities.CaseEntity.CaseStageState;
import io.camunda.search.entities.CaseEntity.CaseState;
import java.util.List;

public class CaseEntityTransformer
    implements ServiceTransformer<io.camunda.webapps.schema.entities.cases.CaseEntity, CaseEntity> {

  @Override
  public CaseEntity apply(final io.camunda.webapps.schema.entities.cases.CaseEntity value) {
    final var stages =
        value.getStages() == null
            ? List.<CaseStageEntity>of()
            : value.getStages().stream().map(CaseEntityTransformer::toStage).toList();
    return new CaseEntity(
        value.getKey(),
        value.getProcessInstanceKey(),
        value.getProcessDefinitionKey(),
        value.getBpmnProcessId(),
        value.getTenantId(),
        emptyToNull(value.getBusinessId()),
        emptyToNull(value.getTitle()),
        CaseState.valueOf(value.getState().name()),
        value.getStartDate(),
        value.getEndDate(),
        value.getStartElementIds() == null ? List.of() : value.getStartElementIds(),
        stages);
  }

  private static CaseStageEntity toStage(
      final io.camunda.webapps.schema.entities.cases.CaseStageEntity stage) {
    return new CaseStageEntity(
        stage.getElementId(),
        emptyToNull(stage.getName()),
        stage.isAlternative(),
        CaseStageState.valueOf(stage.getState().name()),
        stage.getNextElementIds() == null ? List.of() : stage.getNextElementIds());
  }

  private static String emptyToNull(final String value) {
    return value == null || value.isEmpty() ? null : value;
  }
}
