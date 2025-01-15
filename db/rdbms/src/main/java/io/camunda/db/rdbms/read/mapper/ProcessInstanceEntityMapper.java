/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.mapper;

import io.camunda.db.rdbms.write.domain.ProcessInstanceDbModel;
import io.camunda.search.entities.ProcessInstanceEntity;

public class ProcessInstanceEntityMapper {

  public static ProcessInstanceEntity toEntity(final ProcessInstanceDbModel dbModel) {
    return new ProcessInstanceEntity(
        dbModel.processInstanceKey(),
        dbModel.processDefinitionId(),
        null,
        null,
        null,
        dbModel.processDefinitionKey(),
        dbModel.parentProcessInstanceKey(),
        null,
        dbModel.startDate(),
        dbModel.endDate(),
        dbModel.state(),
        null,
        dbModel.tenantId());
  }
}
