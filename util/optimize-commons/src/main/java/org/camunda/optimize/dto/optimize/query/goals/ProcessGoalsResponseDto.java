/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.goals;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

import java.util.List;

@Data
@FieldNameConstants
@AllArgsConstructor
@NoArgsConstructor
public class ProcessGoalsResponseDto {

  private String processName;
  private String processDefinitionKey;
  private String owner;
  private List<ProcessDurationGoalDto> durationGoals;

  public static ProcessGoalsResponseDto from(ProcessGoalsDto processGoalsDto, String processName) {
    final ProcessGoalsResponseDto responseDto = new ProcessGoalsResponseDto();
    responseDto.setProcessName(processName);
    responseDto.setProcessDefinitionKey(processGoalsDto.getProcessDefinitionKey());
    responseDto.setOwner(processGoalsDto.getOwner());
    responseDto.setDurationGoals(processGoalsDto.getDurationGoals());
    return responseDto;
  }

}
