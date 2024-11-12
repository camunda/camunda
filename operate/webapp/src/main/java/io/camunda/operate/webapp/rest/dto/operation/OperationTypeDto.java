/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest.dto.operation;

import io.camunda.webapps.schema.entities.operation.OperationType;

public enum OperationTypeDto {
  RESOLVE_INCIDENT,
  CANCEL_PROCESS_INSTANCE,
  DELETE_PROCESS_INSTANCE,
  ADD_VARIABLE,
  UPDATE_VARIABLE,
  MODIFY_PROCESS_INSTANCE,
  DELETE_DECISION_DEFINITION,
  DELETE_PROCESS_DEFINITION,
  MIGRATE_PROCESS_INSTANCE,
  UNSPECIFIED,
  UNKNOWN;

  public static OperationTypeDto getType(OperationType type) {
    if (type == null) {
      return UNSPECIFIED;
    }
    final OperationTypeDto typeDto = valueOf(type.name());
    if (typeDto == null) {
      return UNKNOWN;
    }
    return typeDto;
  }
}
