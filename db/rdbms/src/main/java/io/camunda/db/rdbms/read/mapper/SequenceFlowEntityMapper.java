/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.mapper;

import io.camunda.db.rdbms.write.domain.SequenceFlowDbModel;
import io.camunda.search.entities.SequenceFlowEntity;

public class SequenceFlowEntityMapper {

  public static SequenceFlowEntity toEntity(final SequenceFlowDbModel dbModel) {
    return new SequenceFlowEntity(
        dbModel.sequenceFlowId(),
        nullToEmpty(dbModel.flowNodeId()),
        dbModel.processInstanceKey(),
        dbModel.rootProcessInstanceKey(),
        dbModel.processDefinitionKey(),
        nullToEmpty(dbModel.processDefinitionId()),
        nullToEmpty(dbModel.tenantId()));
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
