/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.process.single.flownode.frequency.groupby.date.distributedby.none;

import org.assertj.core.groups.Tuple;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.single.ModelElementFrequencyByModelElementDateReportEvaluationIT;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator.IN;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator.NOT_IN;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.util.BpmnModels.getTripleUserTaskDiagram;

public abstract class FlowNodeFrequencyByFlowNodeDateReportEvaluationIT
  extends ModelElementFrequencyByModelElementDateReportEvaluationIT {

  private static Stream<Arguments> viewLevelAssigneeFilterScenarios() {
    return Stream.of(
      Arguments.of(
        IN,
        new String[]{SECOND_USER},
        Collections.singletonList(Tuple.tuple("2021-01-03T00:00:00.000+0100", 1.))
      ),
      Arguments.of(
        IN,
        new String[]{DEFAULT_USERNAME, SECOND_USER, null},
        Arrays.asList(
          Tuple.tuple("2021-01-02T00:00:00.000+0100", 1.),
          Tuple.tuple("2021-01-03T00:00:00.000+0100", 1.),
          Tuple.tuple("2021-01-04T00:00:00.000+0100", 1.)
        )
      ),
      Arguments.of(
        NOT_IN,
        new String[]{SECOND_USER},
        Arrays.asList(
          Tuple.tuple("2021-01-02T00:00:00.000+0100", 1.),
          Tuple.tuple("2021-01-03T00:00:00.000+0100", 0.),
          Tuple.tuple("2021-01-04T00:00:00.000+0100", 1.)
        )
      ),
      Arguments.of(
        NOT_IN,
        new String[]{DEFAULT_USERNAME, SECOND_USER},
        Collections.singletonList(
          Tuple.tuple("2021-01-04T00:00:00.000+0100", 1.)
        )
      )
    );
  }

  @ParameterizedTest
  @MethodSource("viewLevelAssigneeFilterScenarios")
  public void viewLevelFilterByAssigneeOnlyIncludesFlowNodesMatchingFilter(final MembershipFilterOperator filterOperator,
                                                                           final String[] filterValues,
                                                                           final List<Tuple> expectedResult) {
    // given
    engineIntegrationExtension.addUser(SECOND_USER, SECOND_USER_FIRST_NAME, SECOND_USER_LAST_NAME);
    engineIntegrationExtension.grantAllAuthorizations(SECOND_USER);
    final ProcessDefinitionEngineDto processDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(getTripleUserTaskDiagram());
    final ProcessInstanceEngineDto processInstanceDto =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(
      DEFAULT_USERNAME, DEFAULT_PASSWORD, processInstanceDto.getId()
    );
    engineIntegrationExtension.finishAllRunningUserTasks(
      SECOND_USER, SECOND_USERS_PASSWORD, processInstanceDto.getId()
    );
    engineIntegrationExtension.completeUserTaskWithoutClaim(processInstanceDto.getId());

    changeModelElementDate(processInstanceDto, START_EVENT, OffsetDateTime.parse("2021-01-01T10:00:00+01:00"));
    changeModelElementDate(processInstanceDto, USER_TASK_1, OffsetDateTime.parse("2021-01-02T10:00:00+01:00"));
    changeModelElementDate(processInstanceDto, USER_TASK_2, OffsetDateTime.parse("2021-01-03T10:00:00+01:00"));
    changeModelElementDate(processInstanceDto, USER_TASK_3, OffsetDateTime.parse("2021-01-04T10:00:00+01:00"));
    changeModelElementDate(processInstanceDto, END_EVENT, OffsetDateTime.parse("2021-01-05T00:10:00+01:00"));
    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    final List<ProcessFilterDto<?>> assigneeFilter = ProcessFilterBuilder
      .filter().assignee().ids(filterValues).operator(filterOperator)
      .filterLevel(FilterApplicationLevel.VIEW)
      .add().buildList();
    reportData.setFilter(assigneeFilter);
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    assertThat(result.getFirstMeasureData())
      .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
      .containsExactlyInAnyOrderElementsOf(expectedResult);
  }

  private static Stream<Arguments> viewLevelCandidateGroupFilterScenarios() {
    return Stream.of(
      Arguments.of(
        IN,
        new String[]{SECOND_CANDIDATE_GROUP_ID},
        Collections.singletonList(Tuple.tuple("2021-01-03T00:00:00.000+0100", 1.))
      ),
      Arguments.of(
        IN,
        new String[]{FIRST_CANDIDATE_GROUP_ID, SECOND_CANDIDATE_GROUP_ID, null},
        Arrays.asList(
          Tuple.tuple("2021-01-02T00:00:00.000+0100", 1.),
          Tuple.tuple("2021-01-03T00:00:00.000+0100", 1.),
          Tuple.tuple("2021-01-04T00:00:00.000+0100", 1.)
        )
      ),
      Arguments.of(
        NOT_IN,
        new String[]{SECOND_CANDIDATE_GROUP_ID},
        Arrays.asList(
          Tuple.tuple("2021-01-02T00:00:00.000+0100", 1.),
          Tuple.tuple("2021-01-03T00:00:00.000+0100", 0.),
          Tuple.tuple("2021-01-04T00:00:00.000+0100", 1.)
        )
      ),
      Arguments.of(
        NOT_IN,
        new String[]{FIRST_CANDIDATE_GROUP_ID, SECOND_CANDIDATE_GROUP_ID},
        Collections.singletonList(
          Tuple.tuple("2021-01-04T00:00:00.000+0100", 1.)
        )
      )
    );
  }

  @ParameterizedTest
  @MethodSource("viewLevelCandidateGroupFilterScenarios")
  public void viewLevelFilterByCandidateGroupOnlyIncludesFlowNodesMatchingFilter(final MembershipFilterOperator filterOperator,
                                                                                 final String[] filterValues,
                                                                                 final List<Tuple> expectedResult) {
    // given
    engineIntegrationExtension.createGroup(FIRST_CANDIDATE_GROUP_ID, FIRST_CANDIDATE_GROUP_NAME);
    engineIntegrationExtension.createGroup(SECOND_CANDIDATE_GROUP_ID, SECOND_CANDIDATE_GROUP_NAME);
    final ProcessDefinitionEngineDto processDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(getTripleUserTaskDiagram());
    final ProcessInstanceEngineDto processInstanceDto =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(SECOND_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();

    changeModelElementDate(processInstanceDto, START_EVENT, OffsetDateTime.parse("2021-01-01T10:00:00+01:00"));
    changeModelElementDate(processInstanceDto, USER_TASK_1, OffsetDateTime.parse("2021-01-02T10:00:00+01:00"));
    changeModelElementDate(processInstanceDto, USER_TASK_2, OffsetDateTime.parse("2021-01-03T10:00:00+01:00"));
    changeModelElementDate(processInstanceDto, USER_TASK_3, OffsetDateTime.parse("2021-01-04T10:00:00+01:00"));
    changeModelElementDate(processInstanceDto, END_EVENT, OffsetDateTime.parse("2021-01-05T00:10:00+01:00"));
    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    final List<ProcessFilterDto<?>> assigneeFilter = ProcessFilterBuilder
      .filter().candidateGroups().ids(filterValues).operator(filterOperator)
      .filterLevel(FilterApplicationLevel.VIEW)
      .add().buildList();
    reportData.setFilter(assigneeFilter);
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    assertThat(result.getFirstMeasureData())
      .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
      .containsExactlyInAnyOrderElementsOf(expectedResult);
  }

  @Override
  protected void startInstancesWithDayRangeForDefinition(ProcessDefinitionEngineDto processDefinition,
                                                         ZonedDateTime min,
                                                         ZonedDateTime max) {
    final ProcessInstanceEngineDto instance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    changeModelElementDate(instance, START_EVENT, min.toOffsetDateTime());
    changeModelElementDate(instance, END_EVENT, max.toOffsetDateTime());
  }

  @Override
  protected ProcessDefinitionEngineDto deployTwoModelElementDefinition() {
    return deployStartEndDefinition();
  }

  @Override
  protected ProcessDefinitionEngineDto deploySimpleModelElementDefinition() {
    return deployStartEndDefinition();
  }

  @Override
  protected ProcessInstanceEngineDto startAndCompleteInstance(String definitionId) {
    return engineIntegrationExtension.startProcessInstance(definitionId);
  }

  @Override
  protected ProcessInstanceEngineDto startAndCompleteInstanceWithDates(String definitionId,
                                                                       OffsetDateTime firstElementDate,
                                                                       OffsetDateTime secondElementDate) {
    ProcessInstanceEngineDto processInstanceDto =
      engineIntegrationExtension.startProcessInstance(definitionId);
    changeModelElementDate(processInstanceDto, START_EVENT, firstElementDate);
    changeModelElementDate(processInstanceDto, END_EVENT, secondElementDate);
    return processInstanceDto;
  }

  @Override
  protected ProcessViewEntity getExpectedViewEntity() {
    return ProcessViewEntity.FLOW_NODE;
  }

}
