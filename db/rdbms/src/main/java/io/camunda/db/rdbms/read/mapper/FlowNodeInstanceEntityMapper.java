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
        nullToEmpty(dbModel.flowNodeId()),
        nullToEmpty(dbModel.flowNodeName()),
        dbModel.treePath(),
        dbModel.type(),
        dbModel.state(),
        dbModel.hasIncident(),
        dbModel.incidentKey(),
        nullToEmpty(dbModel.processDefinitionId()),
        nullToEmpty(dbModel.tenantId()),
        dbModel.partitionId());
  }

  /**
   * Oracle treats empty strings as NULL. This method converts null values back to empty strings for
   * fields that are required (non-nullable) in the API specification but may legitimately be empty
   * (e.g., protobuf default values).
   */
  private static String nullToEmpty(final String value) {
    return value == null ? "" : value;
  }
}
