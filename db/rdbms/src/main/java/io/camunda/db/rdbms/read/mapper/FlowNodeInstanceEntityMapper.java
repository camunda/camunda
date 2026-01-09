/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.mapper;

import io.camunda.db.rdbms.write.domain.FlowNodeInstanceDbModel;
import io.camunda.search.entities.FlowNodeInstanceEntity;

public class FlowNodeInstanceEntityMapper {

  public static FlowNodeInstanceEntity toEntity(final FlowNodeInstanceDbModel dbModel) {
    return new FlowNodeInstanceEntity(
        dbModel.flowNodeInstanceKey(),
        dbModel.processInstanceKey(),
        dbModel.rootProcessInstanceKey(),
        dbModel.processDefinitionKey(),
        dbModel.startDate(),
        dbModel.endDate(),
        dbModel.flowNodeId(),
        dbModel.flowNodeName(),
        dbModel.treePath(),
        dbModel.type(),
        dbModel.state(),
        dbModel.hasIncident(),
        dbModel.incidentKey(),
        dbModel.processDefinitionId(),
        dbModel.tenantId(),
        dbModel.partitionId());
  }
}
