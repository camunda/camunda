/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.webapp.rest.dto.listview;

import org.camunda.operate.entities.listview.WorkflowInstanceState;

public enum WorkflowInstanceStateDto {

  ACTIVE,
  INCIDENT,
  COMPLETED,
  CANCELED,
  UNKNOWN,
  UNSPECIFIED;

  public static WorkflowInstanceStateDto getState(WorkflowInstanceState state) {
    if (state == null) {
      return UNSPECIFIED;
    }
    WorkflowInstanceStateDto stateDto = valueOf(state.name());
    if (stateDto == null) {
      return UNKNOWN;
    }
    return stateDto;
  }

}
