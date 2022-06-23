/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.filter.process;

import org.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.HyperMapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.single.incident.duration.IncidentDataDeployer;
import org.camunda.optimize.service.es.report.util.HyperMapAsserter;
import org.camunda.optimize.service.es.report.util.MapResultAsserter;
import org.camunda.optimize.service.util.ProcessReportDataType;
import org.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.util.BpmnModels;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.camunda.optimize.dto.optimize.ReportConstants.APPLIED_TO_ALL_DEFINITIONS;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.AVERAGE;
import static org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel.VIEW;
import static org.camunda.optimize.service.es.report.process.single.incident.duration.IncidentDataDeployer.IncidentProcessType.ONE_TASK;
import static org.camunda.optimize.service.es.report.process.single.incident.duration.IncidentDataDeployer.IncidentProcessType.TWO_SEQUENTIAL_TASKS;
import static org.camunda.optimize.util.BpmnModels.END_EVENT;
import static org.camunda.optimize.util.BpmnModels.SERVICE_TASK_ID_1;
import static org.camunda.optimize.util.BpmnModels.SERVICE_TASK_ID_2;
import static org.camunda.optimize.util.BpmnModels.SERVICE_TASK_NAME_1;
import static org.camunda.optimize.util.BpmnModels.SERVICE_TASK_NAME_2;
import static org.camunda.optimize.util.BpmnModels.START_EVENT;
import static org.camunda.optimize.util.BpmnModels.USER_TASK_1;
import static org.camunda.optimize.util.BpmnModels.USER_TASK_2;

public class MultiDefinitionViewFilterIT extends AbstractFilterIT {

  private static final String DEFINITION_KEY_1 = "key1";
  private static final String DEFINITION_IDENTIFIER_1 = "id1";
  private static final String DEFINITION_KEY_2 = "key2";
  private static final String DEFINITION_IDENTIFIER_2 = "id2";
  private static final String VAR_NAME = "var1";
  private static final String VAR_VALUE = "val1";
  private static final Map<String, Object> VARIABLE_MAP = Map.of(VAR_NAME, VAR_VALUE);
  private static final String OTHER_USER_TASK_1 = "otherTask1";
  private static final String OTHER_USER_TASK_2 = "otherTask2";
  public static final String FIRST_CLAIM_USER = "firstClaimUser";
  public static final String SECOND_CLAIM_USER = "secondClaimUser";

  @Test
  public void flowNodeViewFilterAppliesToGroupByFlowNodeDataOfDefinitionSetInAppliedTo() {
    // given
    final ProcessInstanceEngineDto secondUserTaskStartedInstance = engineIntegrationExtension
      .deployAndStartProcessWithVariables(BpmnModels.getDoubleUserTaskDiagram(DEFINITION_KEY_1), VARIABLE_MAP);
    engineIntegrationExtension.finishAllRunningUserTasks(secondUserTaskStartedInstance.getId());
    engineIntegrationExtension
      .startProcessInstance(secondUserTaskStartedInstance.getDefinitionId());

    final ProcessInstanceEngineDto otherDefinitionSecondUserTaskStartedInstance = engineIntegrationExtension
      // second definition uses different userTask Name
      .deployAndStartProcessWithVariables(
        BpmnModels.getDoubleUserTaskDiagram(DEFINITION_KEY_2, OTHER_USER_TASK_1, OTHER_USER_TASK_2),
        VARIABLE_MAP
      );
    engineIntegrationExtension.finishAllRunningUserTasks(otherDefinitionSecondUserTaskStartedInstance.getId());
    engineIntegrationExtension.startProcessInstance(otherDefinitionSecondUserTaskStartedInstance.getDefinitionId());

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReportDataWithTwoDefinitions(
      ProcessReportDataType.FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE
    );
    reportData.setFilter(
      ProcessFilterBuilder.filter()
        // only catch the second user task flow node instances (thus also only catching 2 of 4 process instances)
        .executedFlowNodes()
        .filterLevel(VIEW)
        .operator(MembershipFilterOperator.NOT_IN)
        .ids(START_EVENT, USER_TASK_1)
        .appliedTo(DEFINITION_IDENTIFIER_1)
        .add()
        .executedFlowNodes()
        .filterLevel(VIEW)
        .operator(MembershipFilterOperator.NOT_IN)
        .ids(START_EVENT, OTHER_USER_TASK_1)
        .appliedTo(DEFINITION_IDENTIFIER_2)
        .add()
        .buildList()

    );
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient
      .evaluateMapReport(reportData)
      .getResult();

    // then
    // @formatter:off
    MapResultAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(4L)
      .measure(ViewProperty.FREQUENCY)
        .groupedByContains(OTHER_USER_TASK_2, 1.0)
        .groupedByContains(USER_TASK_2, 1.0)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void flowNodeViewFilterAppliesToGroupByFlowNodeDataOfSpecificDefinitionVersion() {
    // given
    final ProcessInstanceEngineDto completedProcessInstanceDefinition1 = engineIntegrationExtension
      .deployAndStartProcess(BpmnModels.getSingleUserTaskDiagram(DEFINITION_KEY_1, USER_TASK_1));
    engineIntegrationExtension.finishAllRunningUserTasks(completedProcessInstanceDefinition1.getId());
    final ProcessInstanceEngineDto runningProcessInstanceDefinition1 = engineIntegrationExtension
      .startProcessInstance(completedProcessInstanceDefinition1.getDefinitionId());

    // same definition key but new version
    final ProcessInstanceEngineDto completedProcessInstanceDefinition2 = engineIntegrationExtension
      .deployAndStartProcess(BpmnModels.getSingleUserTaskDiagram(DEFINITION_KEY_1, USER_TASK_2));
    engineIntegrationExtension.finishAllRunningUserTasks(completedProcessInstanceDefinition2.getId());
    engineIntegrationExtension.startProcessInstance(completedProcessInstanceDefinition2.getDefinitionId());

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReportDataWithTwoDefinitions(
      ProcessReportDataType.FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE
    );
    reportData.setDefinitions(List.of(
      new ReportDataDefinitionDto(DEFINITION_IDENTIFIER_1, DEFINITION_KEY_1, List.of("1")),
      new ReportDataDefinitionDto(DEFINITION_IDENTIFIER_2, DEFINITION_KEY_1, List.of("2"))
    ));
    reportData.setFilter(
      ProcessFilterBuilder.filter().completedInstancesOnly().appliedTo(DEFINITION_IDENTIFIER_2).add().buildList()
    );
    reportData.setFilter(
      ProcessFilterBuilder.filter()
        // exclude user task two from version 2
        .executedFlowNodes()
        .filterLevel(VIEW)
        .operator(MembershipFilterOperator.NOT_IN)
        .ids(USER_TASK_2)
        .appliedTo(DEFINITION_IDENTIFIER_2)
        .add()
        .buildList()

    );
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient
      .evaluateMapReport(reportData)
      .getResult();

    // then
    // @formatter:off
    MapResultAsserter.asserter()
      .processInstanceCount(4L)
      .processInstanceCountWithoutFilters(4L)
      .measure(ViewProperty.FREQUENCY)
        .groupedByContains(END_EVENT, 2.0)
        .groupedByContains(START_EVENT, 4.0)
        .groupedByContains(USER_TASK_1, 2.0)
      .doAssert(result);
    // @formatter:on
  }

  @ParameterizedTest
  @MethodSource("allDefinitionsAppliedToValues")
  public void flowNodeViewFilterAppliesToGroupByFlowNodeDataOfAllDefinitionsPresent(final List<String> appliedTo) {
    // given
    final ProcessInstanceEngineDto completedProcessInstanceDefinition1 = engineIntegrationExtension
      .deployAndStartProcess(BpmnModels.getSingleUserTaskDiagram(DEFINITION_KEY_1));
    engineIntegrationExtension.finishAllRunningUserTasks(completedProcessInstanceDefinition1.getId());
    engineIntegrationExtension.startProcessInstance(completedProcessInstanceDefinition1.getDefinitionId());

    final ProcessInstanceEngineDto completedProcessInstanceDefinition2 = engineIntegrationExtension
      .deployAndStartProcess(BpmnModels.getSingleUserTaskDiagram(DEFINITION_KEY_2));
    engineIntegrationExtension.finishAllRunningUserTasks(completedProcessInstanceDefinition2.getId());
    engineIntegrationExtension.startProcessInstance(completedProcessInstanceDefinition2.getDefinitionId());

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReportDataWithTwoDefinitions(
      ProcessReportDataType.FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE
    );
    reportData.setFilter(
      ProcessFilterBuilder.filter()
        .executedFlowNodes()
        .filterLevel(VIEW)
        .operator(MembershipFilterOperator.NOT_IN)
        .ids(USER_TASK_1)
        .appliedTo(appliedTo)
        .add()
        .buildList()
    );
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient
      .evaluateMapReport(reportData)
      .getResult();

    // then
    // @formatter:off
    MapResultAsserter.asserter()
      .processInstanceCount(4L)
      .processInstanceCountWithoutFilters(4L)
      .measure(ViewProperty.FREQUENCY)
        .groupedByContains(END_EVENT, 2.0)
        .groupedByContains(START_EVENT, 4.0)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void flowNodeViewFilterAppliesToGroupByFlowNodeDataOfDefinitionSetInAppliedToMixOfAppliedToAllAndSpecific() {
    // given
    final ProcessInstanceEngineDto secondUserTaskStartedInstance = engineIntegrationExtension
      .deployAndStartProcessWithVariables(BpmnModels.getDoubleUserTaskDiagram(DEFINITION_KEY_1), VARIABLE_MAP);
    engineIntegrationExtension.finishAllRunningUserTasks(secondUserTaskStartedInstance.getId());
    engineIntegrationExtension
      .startProcessInstance(secondUserTaskStartedInstance.getDefinitionId());

    final ProcessInstanceEngineDto otherDefinitionSecondUserTaskStartedInstance = engineIntegrationExtension
      // second definition uses different userTask Name
      .deployAndStartProcessWithVariables(
        BpmnModels.getDoubleUserTaskDiagram(DEFINITION_KEY_2, OTHER_USER_TASK_1, OTHER_USER_TASK_2),
        VARIABLE_MAP
      );
    engineIntegrationExtension.finishAllRunningUserTasks(otherDefinitionSecondUserTaskStartedInstance.getId());
    engineIntegrationExtension.startProcessInstance(otherDefinitionSecondUserTaskStartedInstance.getDefinitionId());

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReportDataWithTwoDefinitions(
      ProcessReportDataType.FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE
    );
    reportData.setFilter(
      ProcessFilterBuilder.filter()
        // exclude the start events of all process (catches 4/4 instances as all have other flow node instances)
        .executedFlowNodes()
        .filterLevel(VIEW)
        .operator(MembershipFilterOperator.NOT_IN)
        .ids(START_EVENT)
        .appliedTo(APPLIED_TO_ALL_DEFINITIONS)
        .add()
        .executedFlowNodes()
        .filterLevel(VIEW)
        .operator(MembershipFilterOperator.NOT_IN)
        .ids(OTHER_USER_TASK_1)
        .appliedTo(DEFINITION_IDENTIFIER_2)
        .add()
        .buildList()

    );
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient
      .evaluateMapReport(reportData)
      .getResult();

    // then
    // @formatter:off
    MapResultAsserter.asserter()
      .processInstanceCount(4L)
      .processInstanceCountWithoutFilters(4L)
      .measure(ViewProperty.FREQUENCY)
        .groupedByContains(OTHER_USER_TASK_2, 1.0)
        .groupedByContains(USER_TASK_1, 2.0)
        .groupedByContains(USER_TASK_2, 1.0)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void flowNodeViewFilterAppliesToFlowNodeDistributeByDataOfDefinitionSetInAppliedTo() {
    // given
    final ProcessInstanceEngineDto firstDefinitionCompletedInstance = engineIntegrationExtension
      .deployAndStartProcessWithVariables(BpmnModels.getDoubleUserTaskDiagram(DEFINITION_KEY_1), VARIABLE_MAP);
    engineIntegrationExtension.finishAllRunningUserTasks(firstDefinitionCompletedInstance.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(firstDefinitionCompletedInstance.getId());
    engineDatabaseExtension.changeAllFlowNodeTotalDurations(firstDefinitionCompletedInstance.getId(), 1);
    engineIntegrationExtension.startProcessInstance(firstDefinitionCompletedInstance.getDefinitionId());

    final ProcessInstanceEngineDto secondDefinitionCompletedInstance = engineIntegrationExtension
      // second definition uses different userTask Name
      .deployAndStartProcessWithVariables(
        BpmnModels.getDoubleUserTaskDiagram(DEFINITION_KEY_2, OTHER_USER_TASK_1, OTHER_USER_TASK_2),
        VARIABLE_MAP
      );
    engineIntegrationExtension.finishAllRunningUserTasks(secondDefinitionCompletedInstance.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(secondDefinitionCompletedInstance.getId());
    engineDatabaseExtension
      .changeAllFlowNodeTotalDurations(secondDefinitionCompletedInstance.getId(), 2);
    engineIntegrationExtension.startProcessInstance(secondDefinitionCompletedInstance.getDefinitionId());

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReportDataWithTwoDefinitions(
      ProcessReportDataType.FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE_DURATION_BY_FLOW_NODE
    );
    reportData.setFilter(
      ProcessFilterBuilder.filter()
        // only catch the second user task flow node instances (thus also only catching 2 of 4 process instances)
        .executedFlowNodes()
        .filterLevel(VIEW)
        .operator(MembershipFilterOperator.NOT_IN)
        .ids(START_EVENT, USER_TASK_1)
        .appliedTo(DEFINITION_IDENTIFIER_1)
        .add()
        .executedFlowNodes()
        .filterLevel(VIEW)
        .operator(MembershipFilterOperator.NOT_IN)
        .ids(START_EVENT, OTHER_USER_TASK_1)
        .appliedTo(DEFINITION_IDENTIFIER_2)
        .add()
        .buildList()

    );
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient
      .evaluateHyperMapReport(reportData)
      .getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(4L)
      .measure(ViewProperty.FREQUENCY)
        .groupByContains("1.0")
          .distributedByContains(END_EVENT, 1.0)
          .distributedByContains(OTHER_USER_TASK_2, null)
          .distributedByContains(USER_TASK_2, 1.0)
        .groupByContains("2.0")
          .distributedByContains(END_EVENT, 1.0)
          .distributedByContains(OTHER_USER_TASK_2, 1.0)
          .distributedByContains(USER_TASK_2, null)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void flowNodeViewFilterAppliesToUserTaskDistributeByDataOfDefinitionSetInAppliedTo() {
    // given
    final ProcessInstanceEngineDto firstDefinitionCompletedInstance = engineIntegrationExtension
      .deployAndStartProcessWithVariables(BpmnModels.getDoubleUserTaskDiagram(DEFINITION_KEY_1), VARIABLE_MAP);
    engineIntegrationExtension.finishAllRunningUserTasks(firstDefinitionCompletedInstance.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(firstDefinitionCompletedInstance.getId());
    engineDatabaseExtension.changeAllFlowNodeTotalDurations(firstDefinitionCompletedInstance.getId(), 1);
    engineIntegrationExtension.startProcessInstance(firstDefinitionCompletedInstance.getDefinitionId());

    final ProcessInstanceEngineDto secondDefinitionCompletedInstance = engineIntegrationExtension
      // second definition uses different userTask Name
      .deployAndStartProcessWithVariables(
        BpmnModels.getDoubleUserTaskDiagram(DEFINITION_KEY_2, OTHER_USER_TASK_1, OTHER_USER_TASK_2),
        VARIABLE_MAP
      );
    engineIntegrationExtension.finishAllRunningUserTasks(secondDefinitionCompletedInstance.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(secondDefinitionCompletedInstance.getId());
    engineDatabaseExtension
      .changeAllFlowNodeTotalDurations(secondDefinitionCompletedInstance.getId(), 2);
    engineIntegrationExtension.startProcessInstance(secondDefinitionCompletedInstance.getDefinitionId());

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReportDataWithTwoDefinitions(
      ProcessReportDataType.USER_TASK_FREQ_GROUP_BY_USER_TASK_DURATION_BY_USER_TASK
    );
    reportData.setFilter(
      ProcessFilterBuilder.filter()
        // only catch the second user task flow node instances (thus also only catching 2 of 4 process instances)
        .executedFlowNodes()
        .filterLevel(VIEW)
        .operator(MembershipFilterOperator.NOT_IN)
        .ids(START_EVENT, USER_TASK_1)
        .appliedTo(DEFINITION_IDENTIFIER_1)
        .add()
        .executedFlowNodes()
        .filterLevel(VIEW)
        .operator(MembershipFilterOperator.NOT_IN)
        .ids(START_EVENT, OTHER_USER_TASK_1)
        .appliedTo(DEFINITION_IDENTIFIER_2)
        .add()
        .buildList()

    );
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient
      .evaluateHyperMapReport(reportData)
      .getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(4L)
      .measure(ViewProperty.FREQUENCY)
        .groupByContains("1.0")
          .distributedByContains(OTHER_USER_TASK_2, null)
          .distributedByContains(USER_TASK_2, 1.0)
        .groupByContains("2.0")
          .distributedByContains(OTHER_USER_TASK_2, 1.0)
          .distributedByContains(USER_TASK_2, null)
      .doAssert(result);
    // @formatter:on
  }

  @ParameterizedTest
  @MethodSource("allDefinitionsAppliedToValues")
  public void flowNodeViewFilterAppliesToUserTaskDistributeByDataOfAllDefinitionsPresent(final List<String> appliedTo) {
    // given
    final ProcessInstanceEngineDto completedProcessInstanceDefinition1 = engineIntegrationExtension
      .deployAndStartProcess(BpmnModels.getDoubleUserTaskDiagram(DEFINITION_KEY_1));
    engineIntegrationExtension.finishAllRunningUserTasks(completedProcessInstanceDefinition1.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(completedProcessInstanceDefinition1.getId());
    engineDatabaseExtension.changeAllFlowNodeTotalDurations(completedProcessInstanceDefinition1.getId(), 1);
    engineIntegrationExtension.startProcessInstance(completedProcessInstanceDefinition1.getDefinitionId());

    final ProcessInstanceEngineDto completedProcessInstanceDefinition2 = engineIntegrationExtension
      .deployAndStartProcess(BpmnModels.getDoubleUserTaskDiagram(DEFINITION_KEY_2));
    engineIntegrationExtension.finishAllRunningUserTasks(completedProcessInstanceDefinition2.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(completedProcessInstanceDefinition2.getId());
    engineDatabaseExtension.changeAllFlowNodeTotalDurations(completedProcessInstanceDefinition2.getId(), 2);
    engineIntegrationExtension.startProcessInstance(completedProcessInstanceDefinition2.getDefinitionId());

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReportDataWithTwoDefinitions(
      ProcessReportDataType.USER_TASK_FREQ_GROUP_BY_USER_TASK_DURATION_BY_USER_TASK
    );
    reportData.setFilter(
      ProcessFilterBuilder.filter()
        // only catch the second user task flow node instances (thus also only catching 2 of 4 process instances)
        .executedFlowNodes()
        .filterLevel(VIEW)
        .operator(MembershipFilterOperator.NOT_IN)
        .ids(START_EVENT, USER_TASK_1)
        .appliedTo(appliedTo)
        .add()
        .buildList()

    );
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient
      .evaluateHyperMapReport(reportData)
      .getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(4L)
      .measure(ViewProperty.FREQUENCY)
        .groupByContains("1.0")
          .distributedByContains(USER_TASK_2, 1.0)
        .groupByContains("2.0")
          .distributedByContains(USER_TASK_2, 1.0)
      .doAssert(result);
    // @formatter:on
  }

  private ProcessReportDataDto createReportDataWithTwoDefinitions(final ProcessReportDataType reportType) {
    return TemplatedProcessReportDataBuilder.createReportData()
      .setReportDataType(reportType)
      .definitions(List.of(
        new ReportDataDefinitionDto(DEFINITION_IDENTIFIER_1, DEFINITION_KEY_1),
        new ReportDataDefinitionDto(DEFINITION_IDENTIFIER_2, DEFINITION_KEY_2)
      ))
      .build();
  }

  @Test
  public void identityViewFilterAppliesToUserTaskGroupByDataOfDefinitionSetInAppliedTo() {
    // given
    final ProcessInstanceEngineDto firstDefinitionCompletedInstance = engineIntegrationExtension
      .deployAndStartProcessWithVariables(BpmnModels.getDoubleUserTaskDiagram(DEFINITION_KEY_1), VARIABLE_MAP);
    engineIntegrationExtension
      .claimAllRunningUserTasksWithAssignee(FIRST_CLAIM_USER, firstDefinitionCompletedInstance.getId());
    engineIntegrationExtension.completeUserTaskWithoutClaim(firstDefinitionCompletedInstance.getId());
    engineIntegrationExtension
      .claimAllRunningUserTasksWithAssignee(SECOND_CLAIM_USER, firstDefinitionCompletedInstance.getId());
    engineIntegrationExtension.completeUserTaskWithoutClaim(firstDefinitionCompletedInstance.getId());
    engineDatabaseExtension.changeAllFlowNodeTotalDurations(firstDefinitionCompletedInstance.getId(), 1);
    engineIntegrationExtension.startProcessInstance(firstDefinitionCompletedInstance.getDefinitionId());

    final ProcessInstanceEngineDto secondDefinitionCompletedInstance = engineIntegrationExtension
      // second definition uses different userTask Name
      .deployAndStartProcessWithVariables(
        BpmnModels.getDoubleUserTaskDiagram(DEFINITION_KEY_2, OTHER_USER_TASK_1, OTHER_USER_TASK_2),
        VARIABLE_MAP
      );
    engineIntegrationExtension
      .claimAllRunningUserTasksWithAssignee(FIRST_CLAIM_USER, secondDefinitionCompletedInstance.getId());
    engineIntegrationExtension.completeUserTaskWithoutClaim(secondDefinitionCompletedInstance.getId());
    engineIntegrationExtension
      .claimAllRunningUserTasksWithAssignee(SECOND_CLAIM_USER, secondDefinitionCompletedInstance.getId());
    engineIntegrationExtension.completeUserTaskWithoutClaim(secondDefinitionCompletedInstance.getId());
    engineDatabaseExtension
      .changeAllFlowNodeTotalDurations(secondDefinitionCompletedInstance.getId(), 2);
    engineIntegrationExtension.startProcessInstance(secondDefinitionCompletedInstance.getDefinitionId());

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReportDataWithTwoDefinitions(
      ProcessReportDataType.USER_TASK_DUR_GROUP_BY_ASSIGNEE
    );
    reportData.setFilter(
      ProcessFilterBuilder.filter()
        // only catch the instances with specific assignee from each process (thus only 2 of 4 process instances)
        .assignee()
        .filterLevel(VIEW)
        .ids(FIRST_CLAIM_USER)
        .appliedTo(DEFINITION_IDENTIFIER_1)
        .add()
        .assignee()
        .filterLevel(VIEW)
        .ids(SECOND_CLAIM_USER)
        .appliedTo(DEFINITION_IDENTIFIER_2)
        .add()
        .buildList()

    );
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient
      .evaluateMapReport(reportData)
      .getResult();

    // then
    // @formatter:off
    MapResultAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(4L)
      .measure(ViewProperty.DURATION, new AggregationDto(AVERAGE), UserTaskDurationTime.TOTAL)
        .groupedByContains(FIRST_CLAIM_USER, 1.0)
        .groupedByContains(SECOND_CLAIM_USER, 2.0)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void identityViewFilterAppliesToDistributeByDataOfDefinitionSetInAppliedTo() {
    // given
    final ProcessInstanceEngineDto firstDefinitionCompletedInstance = engineIntegrationExtension
      .deployAndStartProcessWithVariables(BpmnModels.getDoubleUserTaskDiagram(DEFINITION_KEY_1), VARIABLE_MAP);
    engineIntegrationExtension
      .claimAllRunningUserTasksWithAssignee(FIRST_CLAIM_USER, firstDefinitionCompletedInstance.getId());
    engineIntegrationExtension.completeUserTaskWithoutClaim(firstDefinitionCompletedInstance.getId());
    engineIntegrationExtension
      .claimAllRunningUserTasksWithAssignee(SECOND_CLAIM_USER, firstDefinitionCompletedInstance.getId());
    engineIntegrationExtension.completeUserTaskWithoutClaim(firstDefinitionCompletedInstance.getId());
    engineDatabaseExtension.changeAllFlowNodeTotalDurations(firstDefinitionCompletedInstance.getId(), 1);
    engineIntegrationExtension.startProcessInstance(firstDefinitionCompletedInstance.getDefinitionId());

    final ProcessInstanceEngineDto secondDefinitionCompletedInstance = engineIntegrationExtension
      // second definition uses different userTask Name
      .deployAndStartProcessWithVariables(
        BpmnModels.getDoubleUserTaskDiagram(DEFINITION_KEY_2, OTHER_USER_TASK_1, OTHER_USER_TASK_2),
        VARIABLE_MAP
      );
    engineIntegrationExtension
      .claimAllRunningUserTasksWithAssignee(FIRST_CLAIM_USER, secondDefinitionCompletedInstance.getId());
    engineIntegrationExtension.completeUserTaskWithoutClaim(secondDefinitionCompletedInstance.getId());
    engineIntegrationExtension
      .claimAllRunningUserTasksWithAssignee(SECOND_CLAIM_USER, secondDefinitionCompletedInstance.getId());
    engineIntegrationExtension.completeUserTaskWithoutClaim(secondDefinitionCompletedInstance.getId());
    engineDatabaseExtension
      .changeAllFlowNodeTotalDurations(secondDefinitionCompletedInstance.getId(), 2);
    engineIntegrationExtension.startProcessInstance(secondDefinitionCompletedInstance.getDefinitionId());

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReportDataWithTwoDefinitions(
      ProcessReportDataType.USER_TASK_DUR_GROUP_BY_USER_TASK_BY_ASSIGNEE
    );
    reportData.setFilter(
      ProcessFilterBuilder.filter()
        // only catch the instances with specific assignee from each process (thus only 2 of 4 process instances)
        .assignee()
        .filterLevel(VIEW)
        .ids(FIRST_CLAIM_USER)
        .appliedTo(DEFINITION_IDENTIFIER_1)
        .add()
        .assignee()
        .filterLevel(VIEW)
        .ids(SECOND_CLAIM_USER)
        .appliedTo(DEFINITION_IDENTIFIER_2)
        .add()
        .buildList()

    );
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient
      .evaluateHyperMapReport(reportData)
      .getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(4L)
      .measure(ViewProperty.DURATION, AVERAGE, UserTaskDurationTime.TOTAL)
        .groupByContains(OTHER_USER_TASK_2)
          .distributedByContains(FIRST_CLAIM_USER, null)
          .distributedByContains(SECOND_CLAIM_USER, 2.0)
        .groupByContains(USER_TASK_1)
          .distributedByContains(FIRST_CLAIM_USER, 1.0)
          .distributedByContains(SECOND_CLAIM_USER, null)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void incidentViewFilterAppliesToGroupByFlowNodeDataOfDefinitionSetInAppliedTo() {
    // given
    // @formatter:off
    IncidentDataDeployer.dataDeployer(incidentClient)
      .key(DEFINITION_KEY_1)
      .deployProcess(TWO_SEQUENTIAL_TASKS)
      .startProcessInstance()
        .withResolvedAndOpenIncident()
      .startProcessInstance()
        .withResolvedIncident()
      .executeDeployment();
    IncidentDataDeployer.dataDeployer(incidentClient)
      .key(DEFINITION_KEY_2)
      .deployProcess(TWO_SEQUENTIAL_TASKS)
      .startProcessInstance()
        .withResolvedAndOpenIncident()
      .startProcessInstance()
        .withOpenIncident()
      .executeDeployment();
    // @formatter:on

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReportDataWithTwoDefinitions(
      ProcessReportDataType.INCIDENT_FREQ_GROUP_BY_FLOW_NODE
    );
    reportData.setFilter(
      ProcessFilterBuilder.filter()
        // only catch the open incidents from the first definition
        .withOpenIncident()
        .filterLevel(VIEW)
        .appliedTo(DEFINITION_IDENTIFIER_1)
        .add()
        // and the resolved ones from the second
        .withResolvedIncident()
        .filterLevel(VIEW)
        .appliedTo(DEFINITION_IDENTIFIER_2)
        .add()
        .buildList()

    );
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient
      .evaluateMapReport(reportData)
      .getResult();

    // then
    // @formatter:off
    MapResultAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(4L)
      .measure(ViewProperty.FREQUENCY)
      .groupedByContains(SERVICE_TASK_ID_1, 1.0, SERVICE_TASK_NAME_1)
      .groupedByContains(SERVICE_TASK_ID_2, 1.0, SERVICE_TASK_NAME_2)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void incidentViewFilterAppliesToGroupByFlowNodeDataOfSpecificDefinitionVersion() {
    // given
    IncidentDataDeployer.dataDeployer(incidentClient)
      .key(DEFINITION_KEY_1)
      .deployProcess(ONE_TASK)
      .startProcessInstance()
        .withOpenIncident()
      .executeDeployment();
    IncidentDataDeployer.dataDeployer(incidentClient)
      .key(DEFINITION_KEY_1)
      .deployProcess(TWO_SEQUENTIAL_TASKS)
      .startProcessInstance()
        .withOpenIncident()
      .startProcessInstance()
        .withOpenIncident()
      .startProcessInstance()
        .withResolvedIncident()
      .executeDeployment();

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReportDataWithTwoDefinitions(
      ProcessReportDataType.INCIDENT_FREQ_GROUP_BY_FLOW_NODE
    );
    reportData.setDefinitions(List.of(
      new ReportDataDefinitionDto(DEFINITION_IDENTIFIER_1, DEFINITION_KEY_1, List.of("1")),
      new ReportDataDefinitionDto(DEFINITION_IDENTIFIER_2, DEFINITION_KEY_1, List.of("2"))
    ));
    reportData.setFilter(
      ProcessFilterBuilder.filter()
        .withResolvedIncident()
        .filterLevel(VIEW)
        .appliedTo(DEFINITION_IDENTIFIER_2)
        .add()
        .buildList()

    );
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient
      .evaluateMapReport(reportData)
      .getResult();

    // then
    // @formatter:off
    MapResultAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(4L)
      .measure(ViewProperty.FREQUENCY)
      .groupedByContains(SERVICE_TASK_ID_1, 2.0, SERVICE_TASK_NAME_1)
      .doAssert(result);
    // @formatter:on
  }

  @ParameterizedTest
  @MethodSource("allDefinitionsAppliedToValues")
  public void incidentViewFilterAppliesToGroupByFlowNodeDataOfAllDefinitionsPresent(final List<String> appliedTo) {
    // given
    // @formatter:off
    IncidentDataDeployer.dataDeployer(incidentClient)
      .key(DEFINITION_KEY_1)
      .deployProcess(TWO_SEQUENTIAL_TASKS)
      .startProcessInstance()
        .withResolvedAndOpenIncident()
      .executeDeployment();
    IncidentDataDeployer.dataDeployer(incidentClient)
      .key(DEFINITION_KEY_2)
      .deployProcess(TWO_SEQUENTIAL_TASKS)
      .startProcessInstance()
        .withResolvedAndOpenIncident()
      .executeDeployment();
    // @formatter:on

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReportDataWithTwoDefinitions(
      ProcessReportDataType.INCIDENT_FREQ_GROUP_BY_FLOW_NODE
    );
    reportData.setFilter(
      ProcessFilterBuilder.filter()
        .withResolvedIncident()
        .filterLevel(VIEW)
        .appliedTo(appliedTo)
        .add()
        .buildList()
    );
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient
      .evaluateMapReport(reportData)
      .getResult();

    // then
    // @formatter:off
    MapResultAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.FREQUENCY)
      .groupedByContains(SERVICE_TASK_ID_1, 2.0, SERVICE_TASK_NAME_1)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void incidentViewFilterAppliesToGroupByFlowNodeDataOfDefinitionSetInAppliedToMixOfAppliedToAllAndSpecific() {
    // given
    // @formatter:off
    IncidentDataDeployer.dataDeployer(incidentClient)
      .key(DEFINITION_KEY_1)
      .deployProcess(TWO_SEQUENTIAL_TASKS)
      .startProcessInstance()
        .withResolvedAndOpenIncident()
      .executeDeployment();
    IncidentDataDeployer.dataDeployer(incidentClient)
      .key(DEFINITION_KEY_2)
      .deployProcess(TWO_SEQUENTIAL_TASKS)
      .startProcessInstance()
        .withResolvedAndOpenIncident()
      .executeDeployment();
    // @formatter:on

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReportDataWithTwoDefinitions(
      ProcessReportDataType.INCIDENT_FREQ_GROUP_BY_FLOW_NODE
    );
    reportData.setFilter(
      ProcessFilterBuilder.filter()
        .withResolvedIncident()
        .filterLevel(VIEW)
        .appliedTo(APPLIED_TO_ALL_DEFINITIONS)
        .add()
        // this will lead to no results from definition 2 as the all and the specific filter are mutually exclusive:
        // neither flownode in definition 2 matches both filters simultaneously
        .withOpenIncident()
        .filterLevel(VIEW)
        .appliedTo(DEFINITION_IDENTIFIER_2)
        .add()
        .buildList()

    );
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient
      .evaluateMapReport(reportData)
      .getResult();

    // then
    // @formatter:off
    MapResultAsserter.asserter()
      // it catches both instances as on instance level both filters can be satisfied
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.FREQUENCY)
      // still only one flow node from the first definition is present
      .groupedByContains(SERVICE_TASK_ID_1, 1.0, SERVICE_TASK_NAME_1)
      .doAssert(result);
    // @formatter:on
  }

  private static Stream<Arguments> allDefinitionsAppliedToValues() {
    return Stream.<Arguments>builder()
      .add(Arguments.of((List<String>) null))
      .add(Arguments.of(List.of(APPLIED_TO_ALL_DEFINITIONS)))
      .add(Arguments.of(List.of(DEFINITION_IDENTIFIER_1, DEFINITION_IDENTIFIER_2)))
      .build();
  }
}
