/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.process.single.flownode.frequency.groupby.duration;

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
import org.camunda.optimize.service.es.report.process.single.ModelElementFrequencyByModelElementDurationIT;
import org.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.util.SuppressionConstants;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator.IN;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator.NOT_IN;
import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_PASSWORD;
import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_USERNAME;
import static org.camunda.optimize.service.util.ProcessReportDataType.FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE_DURATION;
import static org.camunda.optimize.util.BpmnModels.getTripleUserTaskDiagram;

public class FlowNodeFrequencyByFlowNodeDurationIT extends ModelElementFrequencyByModelElementDurationIT {

  @Override
  protected ProcessInstanceEngineDto startProcessInstanceCompleteTaskAndModifyDuration(
    final String definitionId,
    final Number durationInMillis) {
    final ProcessInstanceEngineDto processInstance = engineIntegrationExtension.startProcessInstance(definitionId);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstance.getId());
    engineDatabaseExtension.changeAllFlowNodeTotalDurations(processInstance.getId(), durationInMillis);
    return processInstance;
  }

  @Override
  protected void changeRunningInstanceReferenceDate(final ProcessInstanceEngineDto runningProcessInstance,
                                                    final OffsetDateTime startTime) {
    engineDatabaseExtension.changeFlowNodeStartDate(runningProcessInstance.getId(), USER_TASK_1, startTime);
  }

  @Override
  protected ProcessViewEntity getModelElementView() {
    return ProcessViewEntity.FLOW_NODE;
  }

  @Override
  protected int getNumberOfModelElementsPerInstance() {
    return 3;
  }

  @Override
  protected ProcessReportDataDto createReport(final String processKey, final String definitionVersion) {
    return TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processKey)
      .setProcessDefinitionVersion(definitionVersion)
      .setReportDataType(FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE_DURATION)
      .build();
  }

  @SuppressWarnings(SuppressionConstants.UNUSED)
  private static Stream<Arguments> viewLevelAssigneeFilterScenarios() {
    return Stream.of(
      Arguments.of(
        IN,
        new String[]{SECOND_USER},
        List.of(
          Tuple.tuple("10.0", 0.),
          Tuple.tuple("20.0", 0.),
          Tuple.tuple("30.0", 1.),
          Tuple.tuple("40.0", 0.),
          Tuple.tuple("50.0", 0.)
        )
      ),
      Arguments.of(
        IN,
        new String[]{DEFAULT_USERNAME, SECOND_USER, null},
        List.of(
          Tuple.tuple("10.0", 0.),
          Tuple.tuple("20.0", 1.),
          Tuple.tuple("30.0", 1.),
          Tuple.tuple("40.0", 1.),
          Tuple.tuple("50.0", 0.)
        )
      ),
      Arguments.of(
        NOT_IN,
        new String[]{SECOND_USER},
        List.of(
          Tuple.tuple("10.0", 0.),
          Tuple.tuple("20.0", 1.),
          Tuple.tuple("30.0", 0.),
          Tuple.tuple("40.0", 1.),
          Tuple.tuple("50.0", 0.)
        )
      ),
      Arguments.of(
        NOT_IN,
        new String[]{DEFAULT_USERNAME, SECOND_USER},
        List.of(
          Tuple.tuple("10.0", 0.),
          Tuple.tuple("20.0", 0.),
          Tuple.tuple("30.0", 0.),
          Tuple.tuple("40.0", 1.),
          Tuple.tuple("50.0", 0.)
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
    final ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(
      DEFAULT_USERNAME, DEFAULT_PASSWORD, processInstanceDto.getId()
    );
    engineIntegrationExtension.finishAllRunningUserTasks(
      SECOND_USER, SECOND_USERS_PASSWORD, processInstanceDto.getId()
    );
    engineIntegrationExtension.completeUserTaskWithoutClaim(processInstanceDto.getId());
    engineDatabaseExtension.changeFlowNodeTotalDuration(processInstanceDto.getId(), START_EVENT, 10.);
    engineDatabaseExtension.changeFlowNodeTotalDuration(processInstanceDto.getId(), USER_TASK_1, 20.);
    engineDatabaseExtension.changeFlowNodeTotalDuration(processInstanceDto.getId(), USER_TASK_2, 30.);
    engineDatabaseExtension.changeFlowNodeTotalDuration(processInstanceDto.getId(), USER_TASK_3, 40.);
    engineDatabaseExtension.changeFlowNodeTotalDuration(processInstanceDto.getId(), END_EVENT, 50.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition.getKey(), ALL_VERSIONS);
    final List<ProcessFilterDto<?>> assigneeFilter = ProcessFilterBuilder
      .filter().assignee().ids(filterValues).operator(filterOperator)
      .filterLevel(FilterApplicationLevel.VIEW)
      .add().buildList();
    reportData.setFilter(assigneeFilter);
    // set custom bucket size to make assertions easier
    reportData.getConfiguration().getCustomBucket().setActive(true);
    reportData.getConfiguration().getCustomBucket().setBucketSize(10.);
    reportData.getConfiguration().getCustomBucket().setBaseline(10.);
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
        Arrays.asList(
          Tuple.tuple("10.0", 0.),
          Tuple.tuple("20.0", 0.),
          Tuple.tuple("30.0", 1.),
          Tuple.tuple("40.0", 0.),
          Tuple.tuple("50.0", 0.)
        )
      ),
      Arguments.of(
        IN,
        new String[]{FIRST_CANDIDATE_GROUP_ID, SECOND_CANDIDATE_GROUP_ID, null},
        Arrays.asList(
          Tuple.tuple("10.0", 0.),
          Tuple.tuple("20.0", 1.),
          Tuple.tuple("30.0", 1.),
          Tuple.tuple("40.0", 1.),
          Tuple.tuple("50.0", 0.)
        )
      ),
      Arguments.of(
        NOT_IN,
        new String[]{SECOND_CANDIDATE_GROUP_ID},
        Arrays.asList(
          Tuple.tuple("10.0", 0.),
          Tuple.tuple("20.0", 1.),
          Tuple.tuple("30.0", 0.),
          Tuple.tuple("40.0", 1.),
          Tuple.tuple("50.0", 0.)
        )
      ),
      Arguments.of(
        NOT_IN,
        new String[]{FIRST_CANDIDATE_GROUP_ID, SECOND_CANDIDATE_GROUP_ID},
        Arrays.asList(
          Tuple.tuple("10.0", 0.),
          Tuple.tuple("20.0", 0.),
          Tuple.tuple("30.0", 0.),
          Tuple.tuple("40.0", 1.),
          Tuple.tuple("50.0", 0.)
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
    final ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(SECOND_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();

    engineDatabaseExtension.changeFlowNodeTotalDuration(processInstanceDto.getId(), START_EVENT, 10.);
    engineDatabaseExtension.changeFlowNodeTotalDuration(processInstanceDto.getId(), USER_TASK_1, 20.);
    engineDatabaseExtension.changeFlowNodeTotalDuration(processInstanceDto.getId(), USER_TASK_2, 30.);
    engineDatabaseExtension.changeFlowNodeTotalDuration(processInstanceDto.getId(), USER_TASK_3, 40.);
    engineDatabaseExtension.changeFlowNodeTotalDuration(processInstanceDto.getId(), END_EVENT, 50.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition.getKey(), ALL_VERSIONS);
    final List<ProcessFilterDto<?>> assigneeFilter = ProcessFilterBuilder
      .filter().candidateGroups().ids(filterValues).operator(filterOperator)
      .filterLevel(FilterApplicationLevel.VIEW)
      .add().buildList();
    reportData.setFilter(assigneeFilter);
    // set custom bucket size to make assertions easier
    reportData.getConfiguration().getCustomBucket().setActive(true);
    reportData.getConfiguration().getCustomBucket().setBucketSize(10.);
    reportData.getConfiguration().getCustomBucket().setBaseline(10.);
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    assertThat(result.getFirstMeasureData())
      .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
      .containsExactlyInAnyOrderElementsOf(expectedResult);
  }
}
