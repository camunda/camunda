/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.goals;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
public class ProcessDurationGoalsAndResultsDto {

  List<ProcessDurationGoalDto> goals;
  List<ProcessDurationGoalResultDto> results;

  public ProcessDurationGoalsAndResultsDto() {
    this.goals = new ArrayList<>();
    this.results = new ArrayList<>();
  }

}
