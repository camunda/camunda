/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.rest.dto.listview;

import io.camunda.operate.entities.listview.ProcessInstanceState;

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
