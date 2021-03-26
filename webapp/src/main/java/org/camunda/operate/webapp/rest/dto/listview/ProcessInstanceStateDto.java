/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.webapp.rest.dto.listview;

import org.camunda.operate.entities.listview.ProcessInstanceState;

public enum ProcessInstanceStateDto {

  ACTIVE,
  INCIDENT,
  COMPLETED,
  CANCELED,
  UNKNOWN,
  UNSPECIFIED;

  public static ProcessInstanceStateDto getState(ProcessInstanceState state) {
    if (state == null) {
      return UNSPECIFIED;
    }
    ProcessInstanceStateDto stateDto = valueOf(state.name());
    if (stateDto == null) {
      return UNKNOWN;
    }
    return stateDto;
  }

}
