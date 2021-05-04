/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.rest.dto.operation;

import io.camunda.operate.entities.OperationType;

public enum OperationTypeDto {

  RESOLVE_INCIDENT,
  CANCEL_PROCESS_INSTANCE,
  UPDATE_VARIABLE,
  UNSPECIFIED,
  UNKNOWN;

  public static OperationTypeDto getType(OperationType type) {
    if (type == null) {
      return UNSPECIFIED;
    }
    OperationTypeDto typeDto = valueOf(type.name());
    if (typeDto == null) {
      return UNKNOWN;
    }
    return typeDto;
  }

}
