/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.filter.process;

import org.assertj.core.groups.Tuple;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.HyperMapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.util.BpmnModels;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.LATEST_VERSION;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.util.ProcessReportDataType.FLOW_NODE_DUR_GROUP_BY_FLOW_NODE;
import static org.camunda.optimize.test.util.ProcessReportDataType.FLOW_NODE_DUR_GROUP_BY_FLOW_NODE_START_DATE;
import static org.camunda.optimize.test.util.ProcessReportDataType.FLOW_NODE_DUR_GROUP_BY_VARIABLE;
import static org.camunda.optimize.test.util.ProcessReportDataType.FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE;
import static org.camunda.optimize.test.util.ProcessReportDataType.FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE_DURATION;
import static org.camunda.optimize.test.util.ProcessReportDataType.FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE_START_DATE;
import static org.camunda.optimize.test.util.ProcessReportDataType.FLOW_NODE_FREQ_GROUP_BY_VARIABLE;
import static org.camunda.optimize.test.util.ProcessReportDataType.INCIDENT_FREQ_GROUP_BY_FLOW_NODE;
import static org.camunda.optimize.test.util.ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_START_DATE;
import static org.camunda.optimize.test.util.ProcessReportDataType.USER_TASK_DUR_GROUP_BY_ASSIGNEE;
import static org.camunda.optimize.test.util.ProcessReportDataType.USER_TASK_DUR_GROUP_BY_USER_TASK;
import static org.camunda.optimize.test.util.ProcessReportDataType.USER_TASK_DUR_GROUP_BY_USER_TASK_START_DATE;
import static org.camunda.optimize.test.util.ProcessReportDataType.USER_TASK_FREQ_GROUP_BY_ASSIGNEE;
import static org.camunda.optimize.test.util.ProcessReportDataType.USER_TASK_FREQ_GROUP_BY_USER_TASK;
import static org.camunda.optimize.test.util.ProcessReportDataType.USER_TASK_FREQ_GROUP_BY_USER_TASK_DURATION;
import static org.camunda.optimize.test.util.ProcessReportDataType.USER_TASK_FREQ_GROUP_BY_USER_TASK_START_DATE;
import static org.camunda.optimize.util.BpmnModels.END_EVENT;
import static org.camunda.optimize.util.BpmnModels.SERVICE_TASK_ID_1;
import static org.camunda.optimize.util.BpmnModels.SERVICE_TASK_ID_2;
import static org.camunda.optimize.util.BpmnModels.START_EVENT;
import static org.camunda.optimize.util.BpmnModels.USER_TASK_1;
import static org.camunda.optimize.util.BpmnModels.USER_TASK_2;

public class ExecutedFlowNodeViewLevelFilterIT extends AbstractFilterIT {

  private static final String DEF_KEY = "defKey";
  private static final OffsetDateTime INSTANCE_1_DATE = OffsetDateTime.parse("2021-01-01T01:00:00+01:00");
  private static final OffsetDateTime INSTANCE_2_DATE = OffsetDateTime.parse("2021-01-02T01:00:00+01:00");
  private static final String INSTANCE_1_DATE_STRING = "2021-01-01T00:00:00.000+0100";
  private static final String INSTANCE_2_DATE_STRING = "2021-01-02T00:00:00.000+0100";
  private static final String INSTANCE_1_START_DURATION_STRING = "10.0";
  private static final String INSTANCE_1_USER_TASK_1_DURATION_STRING = "20.0";
  private static final String INSTANCE_2_START_DURATION_STRING = "30.0";
  private static final String INSTANCE_2_USER_TASK_1_DURATION_STRING = "40.0";
  private static final String VARIABLE_1_VALUE = "value1";
  private static final String VARIABLE_2_VALUE = "value2";
  private static final String DEMO_USER = "demo";

  private void setupInstanceData() {
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.grantAllResourceAuthorizationsForKermit(RESOURCE_TYPE_PROCESS_DEFINITION);
    ProcessDefinitionEngineDto processDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(BpmnModels.getDoubleUserTaskDiagram(DEF_KEY));
    Map<String, Object> variables = new HashMap<>();

    variables.put("stringVar", VARIABLE_1_VALUE);
    ProcessInstanceEngineDto instance1 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    engineIntegrationExtension.claimAllRunningUserTasks(instance1.getId());

    variables.put("stringVar", VARIABLE_2_VALUE);
    ProcessInstanceEngineDto instance2 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    engineIntegrationExtension.finishAllRunningUserTasks(KERMIT_USER, KERMIT_USER, instance2.getId());
    engineIntegrationExtension.claimAllRunningUserTasks(KERMIT_USER, KERMIT_USER, instance2.getId());

    engineDatabaseExtension.changeAllFlowNodeStartDates(instance1.getId(), INSTANCE_1_DATE);
    engineDatabaseExtension.changeAllFlowNodeStartDates(instance2.getId(), INSTANCE_2_DATE);
    engineDatabaseExtension.changeAllFlowNodeEndDates(instance1.getId(), INSTANCE_1_DATE);
    engineDatabaseExtension.changeAllFlowNodeEndDates(instance2.getId(), INSTANCE_2_DATE);

    engineDatabaseExtension.changeFlowNodeTotalDuration(instance1.getId(), START_EVENT, 10L);
    engineDatabaseExtension.changeFlowNodeTotalDuration(instance1.getId(), USER_TASK_1, 20L);
    engineDatabaseExtension.changeFlowNodeTotalDuration(instance2.getId(), START_EVENT, 30L);
    engineDatabaseExtension.changeFlowNodeTotalDuration(instance2.getId(), USER_TASK_1, 40L);
    engineDatabaseExtension.changeFlowNodeTotalDuration(instance2.getId(), USER_TASK_2, 50L);

    importAllEngineEntitiesFromScratch();
  }

  // This stream does not include endDate reports as these scenarios are covered by the equivalent startDate report.
  // Similar also for candidateGroup reports.
  private static Stream<Arguments> flowNodeAndUserTaskMapReportTypeAndExpectedResults() {
    return Stream.of(
      Arguments.of(FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE, Arrays.asList(
        new Tuple(START_EVENT, 2.),
        new Tuple(USER_TASK_1, 2.)
      )),
      Arguments.of(FLOW_NODE_DUR_GROUP_BY_FLOW_NODE, Arrays.asList(
        new Tuple(START_EVENT, 20.),
        new Tuple(USER_TASK_1, 30.)
      )),
      Arguments.of(USER_TASK_FREQ_GROUP_BY_USER_TASK, Collections.singletonList(new Tuple(USER_TASK_1, 2.))),
      Arguments.of(USER_TASK_DUR_GROUP_BY_USER_TASK, Collections.singletonList(new Tuple(USER_TASK_1, 30.))),
      Arguments.of(
        FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE_DURATION,
        Arrays.asList(
          new Tuple(INSTANCE_1_START_DURATION_STRING, 1.),
          new Tuple(INSTANCE_1_USER_TASK_1_DURATION_STRING, 1.),
          new Tuple(INSTANCE_2_START_DURATION_STRING, 1.),
          new Tuple(INSTANCE_2_USER_TASK_1_DURATION_STRING, 1.)
        )
      ),
      Arguments.of(
        USER_TASK_FREQ_GROUP_BY_USER_TASK_DURATION,
        Arrays.asList(
          new Tuple(INSTANCE_1_USER_TASK_1_DURATION_STRING, 1.),
          new Tuple(INSTANCE_2_USER_TASK_1_DURATION_STRING, 1.)
        )
      ),
      Arguments.of(
        USER_TASK_FREQ_GROUP_BY_USER_TASK_START_DATE,
        Arrays.asList(
          new Tuple(INSTANCE_1_DATE_STRING, 1.),
          new Tuple(INSTANCE_2_DATE_STRING, 1.)
        )
      ),
      Arguments.of(
        USER_TASK_DUR_GROUP_BY_USER_TASK_START_DATE,
        Arrays.asList(
          new Tuple(INSTANCE_1_DATE_STRING, 20.),
          new Tuple(INSTANCE_2_DATE_STRING, 40.)
        )
      ),
      Arguments.of(
        FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE_START_DATE,
        Arrays.asList(
          new Tuple(INSTANCE_1_DATE_STRING, 2.),
          new Tuple(INSTANCE_2_DATE_STRING, 2.)
        )
      ),
      Arguments.of(
        FLOW_NODE_DUR_GROUP_BY_FLOW_NODE_START_DATE,
        Arrays.asList(
          new Tuple(INSTANCE_1_DATE_STRING, 15.),
          new Tuple(INSTANCE_2_DATE_STRING, 35.)
        )
      ),
      Arguments.of(
        USER_TASK_FREQ_GROUP_BY_ASSIGNEE,
        Arrays.asList(
          new Tuple(DEMO_USER, 1.),
          new Tuple(KERMIT_USER, 1.)
        )
      ),
      Arguments.of(
        USER_TASK_DUR_GROUP_BY_ASSIGNEE,
        Arrays.asList(
          new Tuple(DEMO_USER, 20.),
          new Tuple(KERMIT_USER, 40.)
        )
      ),
      Arguments.of(
        FLOW_NODE_FREQ_GROUP_BY_VARIABLE,
        Arrays.asList(
          new Tuple(VARIABLE_1_VALUE, 2.),
          new Tuple(VARIABLE_2_VALUE, 2.)
        )
      ),
      Arguments.of(
        FLOW_NODE_DUR_GROUP_BY_VARIABLE,
        Arrays.asList(
          new Tuple(VARIABLE_1_VALUE, 15.),
          new Tuple(VARIABLE_2_VALUE, 35.)
        )
      )
    );
  }

  @ParameterizedTest
  @MethodSource("flowNodeAndUserTaskMapReportTypeAndExpectedResults")
  public void notInExecutedFlowNodeFilterWorks_groupByReports(final ProcessReportDataType reportType,
                                                              final List<Tuple> expectedResults) {
    // given one instance with start, userTask1 and userTask2 and one instance with start and userTask1
    setupInstanceData();

    // when filtering out userTask2 (ie a filter that only affects flowNode data and not instance count because both
    // instances have some flowNodes which match the filter)
    final ReportResultResponseDto<List<MapResultEntryDto>> resultDto =
      evaluateReportWithNotInExecutedFlowNodeFilter(reportType, new String[]{USER_TASK_2});

    // then both instances are in the result but the userTask2 bucket is not
    assertThat(resultDto.getInstanceCount()).isEqualTo(2);
    assertThat(resultDto.getInstanceCountWithoutFilters()).isEqualTo(2);
    assertThat(resultDto.getFirstMeasureData())
      // filter to check which buckets have non null/zero results
      .filteredOn(r -> r.getValue() != null && r.getValue() > 0.)
      .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
      .containsExactlyInAnyOrderElementsOf(expectedResults);
  }

  @Test
  public void notInExecutedFlowNodeFilterWorks_distributedByReport() {
    // given one instance with start, userTask1 and userTask2 and one instance with start and userTask1
    setupInstanceData();

    final ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(DEF_KEY)
      .setProcessDefinitionVersion(LATEST_VERSION)
      .setReportDataType(ProcessReportDataType.FLOW_NODE_DUR_GROUP_BY_VARIABLE_BY_FLOW_NODE)
      .setVariableName("stringVar")
      .setVariableType(VariableType.STRING)
      .setFilter(createNotInExecutedFlowNodeFilter(new String[]{USER_TASK_2}))
      .build();
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> resultDto = reportClient
      .evaluateHyperMapReport(reportData).getResult();

    // then both instances are in the result as well as all flowNodes except the filtered out userTask2 bucket
    assertThat(resultDto.getInstanceCount()).isEqualTo(2);
    assertThat(resultDto.getInstanceCountWithoutFilters()).isEqualTo(2);
    assertThat(
      resultDto.getFirstMeasureData().stream()
        .flatMap(hyperMapResultEntryDto -> hyperMapResultEntryDto.getValue().stream())
        .map(MapResultEntryDto::getKey)
        .distinct()
    ).containsExactlyInAnyOrder(START_EVENT, USER_TASK_1, END_EVENT);
  }

  @Test
  public void notInExecutedFlowNodeFilterWorks_incidentReport() {
    // given
    ProcessDefinitionEngineDto processDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(BpmnModels.getTwoExternalTaskProcess(DEF_KEY));

    final ProcessInstanceEngineDto instance1 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), "businessKey1");
    final ProcessInstanceEngineDto instance2 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), "businessKey2");

    // instance1 has 1 resolved incident in serviceTask1 and one open incident in serviceTask2
    incidentClient.createOpenIncidentForInstancesWithBusinessKey(instance1.getBusinessKey());
    engineIntegrationExtension.completeExternalTasks(instance1.getId());
    incidentClient.createOpenIncidentForInstancesWithBusinessKey(instance1.getBusinessKey());
    // instance2 has 1 open incident in serviceTask1
    incidentClient.createOpenIncidentForInstancesWithBusinessKey(instance2.getBusinessKey());
    importAllEngineEntitiesFromScratch();

    // when filtering out service task 2 (ie a filter that only affects flowNode data and not instance count because
    // both instances have an incident which matches the filter)
    ReportResultResponseDto<List<MapResultEntryDto>> resultDto =
      evaluateReportWithNotInExecutedFlowNodeFilter(
        INCIDENT_FREQ_GROUP_BY_FLOW_NODE,
        new String[]{SERVICE_TASK_ID_2}
      );

    // then both instances are in the result but the serviceTask2 bucket is not
    assertThat(resultDto.getInstanceCount()).isEqualTo(2);
    assertThat(resultDto.getInstanceCountWithoutFilters()).isEqualTo(2);
    assertThat(resultDto.getFirstMeasureData())
      .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
      .containsExactly(new Tuple(SERVICE_TASK_ID_1, 2.));

    // when filtering out start and service task 1 (ie a filter that also affects instance count because only
    // instance2 has an incident which matches the filter)
    resultDto =
      evaluateReportWithNotInExecutedFlowNodeFilter(
        INCIDENT_FREQ_GROUP_BY_FLOW_NODE,
        new String[]{START_EVENT, SERVICE_TASK_ID_1}
      );

    // then only the instance that also has an incident in serviceTask2 is in the result
    assertThat(resultDto.getInstanceCount()).isEqualTo(1);
    assertThat(resultDto.getInstanceCountWithoutFilters()).isEqualTo(2);
    assertThat(resultDto.getFirstMeasureData())
      .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
      .containsExactly(new Tuple(SERVICE_TASK_ID_2, 1.));
  }

  @ParameterizedTest
  @MethodSource("flowNodeAndUserTaskMapReportTypeAndExpectedResults")
  public void notInExecutedFlowNodeFilterWorks_viewLevelFilteringAlsoAffectsInstanceCount(final ProcessReportDataType reportType) {
    // given one instance with start, userTask1 and userTask2 and one instance with start and userTask1
    setupInstanceData();

    // when filtering out the start event and the userTask1 (a viewLevel filter that also affects instance count
    // because instance1 does not have any flowNodes matching the filter)
    ReportResultResponseDto<List<MapResultEntryDto>> resultDto = evaluateReportWithNotInExecutedFlowNodeFilter(
      reportType,
      new String[]{START_EVENT, USER_TASK_1}
    );

    // then the instance with only start and userTask1 is filtered out and only the end event bucket is in the result
    assertThat(resultDto.getInstanceCount()).isEqualTo(1);
    assertThat(resultDto.getInstanceCountWithoutFilters()).isEqualTo(2);
  }

  @Test
  public void notInExecutedFlowNodeFilterWorks_viewLevelFilteringAlsoAffectsInstanceCount_nonFlowNodeReport() {
    // given one instance with start, userTask1 and userTask2 and one instance with start and userTask1
    setupInstanceData();

    // when filtering out the start event and the userTask1 (a viewLevel filter that also affects instance count
    // because instance1 does not have any flowNodes matching the filter)
    ReportResultResponseDto<List<MapResultEntryDto>> resultDto = evaluateReportWithNotInExecutedFlowNodeFilter(
      PROC_INST_FREQ_GROUP_BY_START_DATE,
      new String[]{START_EVENT, USER_TASK_1}
    );

    // then the instance with only start and userTask1 is filtered out
    assertThat(resultDto.getInstanceCount()).isEqualTo(1);
    assertThat(resultDto.getInstanceCountWithoutFilters()).isEqualTo(2);
  }

  private ReportResultResponseDto<List<MapResultEntryDto>> evaluateReportWithNotInExecutedFlowNodeFilter(final ProcessReportDataType reportType,
                                                                                                         final String[] idsToFilter) {
    final List<ProcessFilterDto<?>> flowNodeIdFilter = createNotInExecutedFlowNodeFilter(idsToFilter);
    final ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(DEF_KEY)
      .setProcessDefinitionVersion(LATEST_VERSION)
      .setReportDataType(reportType)
      .setGroupByDateInterval(AggregateByDateUnit.DAY)
      .setUserTaskDurationTime(UserTaskDurationTime.TOTAL)
      .setVariableName("stringVar")
      .setVariableType(VariableType.STRING)
      .setFilter(flowNodeIdFilter)
      .build();
    return reportClient.evaluateMapReport(reportData).getResult();
  }

  private List<ProcessFilterDto<?>> createNotInExecutedFlowNodeFilter(final String[] idsToFilter) {
    return ProcessFilterBuilder
      .filter()
      .executedFlowNodes()
      .notInOperator()
      .ids(idsToFilter)
      .filterLevel(FilterApplicationLevel.VIEW)
      .add()
      .buildList();
  }

}
