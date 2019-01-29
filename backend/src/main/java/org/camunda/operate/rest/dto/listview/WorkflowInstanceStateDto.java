package org.camunda.operate.rest.dto.listview;

import org.camunda.operate.entities.WorkflowInstanceState;

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
