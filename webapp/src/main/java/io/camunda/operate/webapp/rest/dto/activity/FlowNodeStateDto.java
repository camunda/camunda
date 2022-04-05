/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.rest.dto.activity;

import io.camunda.operate.entities.FlowNodeState;

public enum FlowNodeStateDto {

  ACTIVE,
  INCIDENT,
  COMPLETED,
  TERMINATED,
  UNSPECIFIED,
  UNKNOWN;

  public static FlowNodeStateDto getState(FlowNodeState state) {
    if (state == null) {
      return UNSPECIFIED;
    }
    FlowNodeStateDto stateDto = valueOf(state.name());
    if (stateDto == null) {
      return UNKNOWN;
    }
    return stateDto;
  }

}
