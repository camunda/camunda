/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.process;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.goals.ProcessDurationGoalDto;
import org.camunda.optimize.dto.optimize.query.goals.ProcessGoalsResponseDto;
import org.camunda.optimize.dto.optimize.rest.sorting.ProcessGoalSorter;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.stream.Stream;

import static org.camunda.optimize.dto.optimize.query.goals.DurationGoalType.SLA_DURATION;
import static org.camunda.optimize.dto.optimize.query.goals.DurationGoalType.TARGET_DURATION;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DurationUnit.DAYS;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DurationUnit.HOURS;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DurationUnit.MILLIS;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DurationUnit.MONTHS;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DurationUnit.SECONDS;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DurationUnit.YEARS;
import static org.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;

public class AbstractProcessGoalsIT extends AbstractIT {

  protected static final String FIRST_PROCESS_DEFINITION_KEY = "firstProcessDefinition";
  protected static final String SECOND_PROCESS_DEFINITION_KEY = "secondProcessDefinition";
  protected static final String DEF_KEY = FIRST_PROCESS_DEFINITION_KEY;
  protected static final String OTHER_TENANT = "otherTenant";

  protected void setGoalsForProcess(final String processDefKey, final List<ProcessDurationGoalDto> goals) {
    embeddedOptimizeExtension.getRequestExecutor()
      .buildUpdateProcessGoalsRequest(processDefKey, goals)
      .execute();
  }

  protected List<ProcessGoalsResponseDto> getProcessGoals() {
    return getProcessGoals(null);
  }

  protected List<ProcessGoalsResponseDto> getProcessGoals(ProcessGoalSorter sorter) {
    return embeddedOptimizeExtension.getRequestExecutor()
      .buildGetProcessGoalsRequest(sorter)
      .executeAndReturnList(ProcessGoalsResponseDto.class, Response.Status.OK.getStatusCode());
  }

  protected ProcessDefinitionEngineDto deploySimpleProcessDefinition(String processDefinitionKey) {
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(getSimpleBpmnDiagram(processDefinitionKey));
  }

  protected static Stream<List<ProcessDurationGoalDto>> invalidGoalsLists() {
    return Stream.of(
      List.of(new ProcessDurationGoalDto(SLA_DURATION, -5., 5, DAYS)),
      List.of(new ProcessDurationGoalDto(SLA_DURATION, 50., -5, MILLIS)),
      List.of(new ProcessDurationGoalDto(TARGET_DURATION, 105., 1, MONTHS)),
      List.of(
        new ProcessDurationGoalDto(SLA_DURATION, 50., 5, SECONDS),
        new ProcessDurationGoalDto(SLA_DURATION, 50., 5, YEARS)
      ),
      List.of(new ProcessDurationGoalDto(TARGET_DURATION, 50., 1, null)),
      List.of(new ProcessDurationGoalDto(null, 50., 1, HOURS))
    );
  }

}
