/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.process;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.tuple.Triple;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.engine.HistoricUserTaskInstanceDto;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DurationUnit;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.ComparisonOperator;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.DurationFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResponseDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.util.BpmnModels;

import javax.ws.rs.core.Response;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.ReportConstants.DEFAULT_TENANT_IDS;
import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_USERNAME;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_FULLNAME;
import static org.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;
import static org.camunda.optimize.util.BpmnModels.getSingleUserTaskDiagram;
import static org.camunda.optimize.util.BpmnModels.getTripleUserTaskDiagram;
import static org.camunda.optimize.util.SuppressionConstants.UNUSED;

public class AbstractProcessDefinitionIT extends AbstractIT {

  protected static final String TEST_ACTIVITY = "testActivity";
  protected static final String BUSINESS_KEY = "aBusinessKey";
  protected static final String END_EVENT = "endEvent";
  protected static final String START_EVENT = "startEvent";
  protected static final String DEFAULT_VARIABLE_NAME = "foo";
  protected static final String DEFAULT_VARIABLE_VALUE = "bar";
  protected static final String USER_TASK_1 = "userTask1";
  protected static final String USER_TASK_2 = "userTask2";
  protected static final String USER_TASK_3 = "userTask3";
  protected static final String USER_TASK_1_NAME = "userTask1Name";
  protected static final String USER_TASK_2_NAME = "userTask2Name";
  protected static final String USER_TASK_3_NAME = "userTask3Name";

  protected static final String FIRST_CANDIDATE_GROUP_ID = "firstGroup";
  protected static final String FIRST_CANDIDATE_GROUP_NAME = "The Crew";
  protected static final String SECOND_CANDIDATE_GROUP_ID = "secondGroup";
  protected static final String SECOND_CANDIDATE_GROUP_NAME = "The Imposters";
  protected static final String SECOND_USER = "secondUser";
  protected static final String SECOND_USERS_PASSWORD = SECOND_USER;
  protected static final String SECOND_USER_FIRST_NAME = "the";
  protected static final String SECOND_USER_LAST_NAME = "other";
  protected static final String SECOND_USER_FULL_NAME = SECOND_USER_FIRST_NAME + " " + SECOND_USER_LAST_NAME;
  protected static final VariableType DEFAULT_VARIABLE_TYPE = VariableType.STRING;
  protected static final String TEST_PROCESS = "aProcess";

  protected static final Map<String, VariableType> varNameToTypeMap = new HashMap<>(VariableType.values().length);

  static {
    varNameToTypeMap.put("dateVar", VariableType.DATE);
    varNameToTypeMap.put("boolVar", VariableType.BOOLEAN);
    varNameToTypeMap.put("shortVar", VariableType.SHORT);
    varNameToTypeMap.put("intVar", VariableType.INTEGER);
    varNameToTypeMap.put("longVar", VariableType.LONG);
    varNameToTypeMap.put("doubleVar", VariableType.DOUBLE);
    varNameToTypeMap.put("stringVar", VariableType.STRING);
  }

  protected ProcessInstanceEngineDto deployAndStartSimpleProcess() {
    return deployAndStartSimpleProcess(null);
  }

  protected ProcessInstanceEngineDto deployAndStartSimpleProcess(String tenantId) {
    return deployAndStartSimpleProcessWithVariables(new HashMap<>(), tenantId);
  }

  protected ProcessInstanceEngineDto deployAndStartSimpleProcessWithVariables(Map<String, Object> variables) {
    return deployAndStartSimpleProcessWithVariables(variables, null);
  }

  private ProcessInstanceEngineDto deployAndStartSimpleProcessWithVariables(Map<String, Object> variables,
                                                                            String tenantId) {
    return engineIntegrationExtension.deployAndStartProcessWithVariables(
      getSimpleBpmnDiagram(),
      variables,
      BUSINESS_KEY,
      tenantId
    );
  }

  protected ProcessInstanceEngineDto deployAndStartSimpleUserTaskProcess() {
    return engineIntegrationExtension.deployAndStartProcess(BpmnModels.getSingleUserTaskDiagram());
  }

  protected ProcessInstanceEngineDto deployAndStartSimpleUserTaskProcess(final Map<String, Object> variables) {
    return engineIntegrationExtension.deployAndStartProcessWithVariables(
      BpmnModels.getSingleUserTaskDiagram(),
      variables
    );
  }

  private void deployAndStartSimpleUserTaskProcessOnTenant(final String key,
                                                           final String tenantId) {
    final BpmnModelInstance processModel = BpmnModels.getSingleUserTaskDiagram(key);
    engineIntegrationExtension.deployAndStartProcess(processModel, tenantId);
  }

  protected ProcessDefinitionEngineDto deploySimpleOneUserTasksDefinition() {
    return deploySimpleOneUserTasksDefinition(TEST_PROCESS, null);
  }

  protected ProcessDefinitionEngineDto deploySimpleOneUserTasksDefinition(String key) {
    return deploySimpleOneUserTasksDefinition(key, null);
  }

  protected ProcessDefinitionEngineDto deploySimpleOneUserTasksDefinition(String key, String tenantId) {
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(getSingleUserTaskDiagram(key), tenantId);
  }

  protected ProcessInstanceEngineDto deployAndStartThreeUserTasksDefinition(final Map<String, Object> variables) {
    return engineIntegrationExtension.deployAndStartProcessWithVariables(getTripleUserTaskDiagram(), variables);
  }

  protected ProcessInstanceEngineDto deployAndStartSimpleServiceTaskProcess() {
    return deployAndStartSimpleServiceTaskProcess(TEST_ACTIVITY);
  }

  protected ProcessInstanceEngineDto deployAndStartSimpleServiceTaskProcess(String activityId) {
    return deployAndStartSimpleServiceTaskProcess(TEST_PROCESS, activityId, null);
  }

  protected ProcessInstanceEngineDto deployAndStartSimpleServiceTaskProcess(String key,
                                                                            String activityId) {
    return deployAndStartSimpleServiceTaskProcess(key, activityId, null);
  }

  protected ProcessInstanceEngineDto deployAndStartSimpleServiceTaskProcess(final Map<String, Object> variables) {
    BpmnModelInstance processModel = BpmnModels.getSingleServiceTaskProcess(TEST_PROCESS);
    return engineIntegrationExtension.deployAndStartProcessWithVariables(processModel, variables);
  }

  protected ProcessInstanceEngineDto deployAndStartSimpleServiceTaskProcess(String key,
                                                                            String activityId,
                                                                            String tenantId) {
    BpmnModelInstance processModel = BpmnModels.getSingleServiceTaskProcess(key, activityId);
    return engineIntegrationExtension.deployAndStartProcessWithVariables(
      processModel, ImmutableMap.of(DEFAULT_VARIABLE_NAME, DEFAULT_VARIABLE_VALUE), tenantId
    );
  }

  protected List<ProcessInstanceEngineDto> deployAndStartSimpleProcesses(int numberOfProcesses) {
    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcessAndGetDefinition();
    return IntStream.range(0, numberOfProcesses)
      .mapToObj(i -> {
        ProcessInstanceEngineDto processInstanceEngineDto = engineIntegrationExtension.startProcessInstance(
          processDefinition.getId());
        processInstanceEngineDto.setProcessDefinitionKey(processDefinition.getKey());
        processInstanceEngineDto.setProcessDefinitionVersion(String.valueOf(processDefinition.getVersion()));
        return processInstanceEngineDto;
      })
      .collect(Collectors.toList());
  }

  protected ProcessDefinitionEngineDto deploySimpleServiceTaskProcessAndGetDefinition() {
    return deploySimpleServiceTaskProcessAndGetDefinition(TEST_PROCESS);
  }

  private ProcessDefinitionEngineDto deploySimpleServiceTaskProcessAndGetDefinition(String key) {
    BpmnModelInstance processModel = BpmnModels.getSingleServiceTaskProcess(key, TEST_ACTIVITY);
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(processModel);
  }

  protected ProcessInstanceEngineDto deployAndStartTwoServiceTaskProcessWithVariables(final Map<String, Object> variables) {
    BpmnModelInstance processModel = BpmnModels.getTwoServiceTasksProcess(TEST_PROCESS);
    return engineIntegrationExtension.deployAndStartProcessWithVariables(processModel, variables);
  }

  protected ProcessInstanceEngineDto deployAndStartTwoServiceTaskProcessWithVariables(final String key,
                                                                                      final Map<String, Object> variables) {
    BpmnModelInstance processModel = BpmnModels.getTwoServiceTasksProcess(key);
    return engineIntegrationExtension.deployAndStartProcessWithVariables(processModel, variables);
  }

  protected ProcessDefinitionEngineDto deploySimpleGatewayProcessDefinition() {
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(
      BpmnModels.getSimpleGatewayProcess(TEST_PROCESS)
    );
  }

  protected String deployAndStartMultiTenantSimpleServiceTaskProcess(final List<String> deployedTenants) {
    final String processKey = "multiTenantProcess";
    deployedTenants.forEach(tenant -> {
      if (tenant != null) {
        engineIntegrationExtension.createTenant(tenant);
      }
      deployAndStartSimpleServiceTaskProcess(processKey, TEST_ACTIVITY, tenant);
    });

    return processKey;
  }

  protected String deployAndStartMultiTenantSimpleUserTaskTaskProcess(final List<String> deployedTenants) {
    final String processKey = "multiTenantProcess";
    deployedTenants.forEach(tenant -> {
      if (tenant != null) {
        engineIntegrationExtension.createTenant(tenant);
      }
      deployAndStartSimpleUserTaskProcessOnTenant(processKey, tenant);
    });

    return processKey;
  }

  protected ProcessInstanceEngineDto deployAndStartLoopingProcess() {
    return deployAndStartLoopingProcess(new HashMap<>());
  }

  protected ProcessInstanceEngineDto deployAndStartLoopingProcess(Map<String, Object> variables) {
    final BpmnModelInstance modelInstance = BpmnModels.getLoopingProcess();
    variables.put("anotherRound", true);
    return engineIntegrationExtension.deployAndStartProcessWithVariables(modelInstance, variables);
  }

  protected List<ProcessInstanceEngineDto> startAndEndProcessInstancesWithGivenRuntime(
    final int numberOfInstances,
    final Duration instanceRuntime,
    final OffsetDateTime startTimeOfFirstInstance) {
    List<ProcessInstanceEngineDto> processInstanceDtos = deployAndStartSimpleProcesses(numberOfInstances);

    for (int i = 0; i < numberOfInstances; i++) {
      final OffsetDateTime startTime = startTimeOfFirstInstance.plus(instanceRuntime.multipliedBy(i));
      final OffsetDateTime endTime = startTime.plus(instanceRuntime);
      engineDatabaseExtension.changeProcessInstanceStartAndEndDate(
        processInstanceDtos.get(i).getId(),
        startTime,
        endTime
      );
    }
    return processInstanceDtos;
  }

  protected ProcessInstanceEngineDto startInstanceAndModifyDuration(final String definitionId,
                                                                    final long durationInMilliseconds) {
    final ProcessInstanceEngineDto processInstance = engineIntegrationExtension
      .startProcessInstance(definitionId);
    changeProcessInstanceDuration(processInstance, durationInMilliseconds);
    return processInstance;
  }

  protected void changeProcessInstanceDuration(final ProcessInstanceEngineDto processInstanceDto,
                                               final long durationInMilliseconds) {
    final OffsetDateTime startDate = LocalDateUtil.getCurrentDateTime();
    final OffsetDateTime endDate = startDate.plus(durationInMilliseconds, ChronoUnit.MILLIS);
    engineDatabaseExtension.changeProcessInstanceStartAndEndDate(processInstanceDto.getId(), startDate, endDate);
  }

  protected void changeActivityDuration(final ProcessInstanceEngineDto processInstance,
                                        final Double durationInMs) {
    engineDatabaseExtension.changeAllFlowNodeTotalDurations(processInstance.getId(), durationInMs.longValue());
  }

  protected ProcessReportDataDto createReportDataSortedDesc(final String definitionKey,
                                                            final String definitionVersion,
                                                            final ProcessReportDataType reportType,
                                                            final AggregateByDateUnit unit) {
    return createReportData(
      definitionKey,
      definitionVersion,
      reportType,
      unit,
      new ReportSortingDto(ReportSortingDto.SORT_BY_KEY, SortOrder.DESC)
    );
  }

  protected ProcessReportDataDto createReportData(final String definitionKey,
                                                  final String definitionVersion,
                                                  final ProcessReportDataType reportType,
                                                  final AggregateByDateUnit unit,
                                                  final ReportSortingDto sorting) {
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder.createReportData()
      .setProcessDefinitionKey(definitionKey)
      .setProcessDefinitionVersion(definitionVersion)
      .setReportDataType(reportType)
      .setGroupByDateInterval(unit)
      .build();
    reportData.getConfiguration().setSorting(sorting);
    return reportData;
  }

  protected String createNewReport(ProcessReportDataDto processReportDataDto) {
    SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto =
      new SingleProcessReportDefinitionRequestDto();
    singleProcessReportDefinitionDto.setData(processReportDataDto);
    singleProcessReportDefinitionDto.setLastModifier("something");
    singleProcessReportDefinitionDto.setName("something");
    singleProcessReportDefinitionDto.setCreated(OffsetDateTime.now());
    singleProcessReportDefinitionDto.setLastModified(OffsetDateTime.now());
    singleProcessReportDefinitionDto.setOwner("something");
    return createNewReport(singleProcessReportDefinitionDto);
  }

  protected String createNewReport(SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest(singleProcessReportDefinitionDto)
      .execute(IdResponseDto.class, Response.Status.OK.getStatusCode())
      .getId();
  }

  protected ReportDataDefinitionDto createReportDataDefinitionDto(final String key) {
    return new ReportDataDefinitionDto(key, Collections.singletonList(ALL_VERSIONS), DEFAULT_TENANT_IDS);
  }

  protected void changeUserTaskIdleDuration(final ProcessInstanceEngineDto processInstanceDto,
                                            final String userTaskKey,
                                            final Number durationInMs) {
    engineIntegrationExtension.getHistoricTaskInstances(processInstanceDto.getId(), userTaskKey)
      .forEach(
        historicUserTaskInstanceDto -> changeUserClaimStartTimestamp(durationInMs, historicUserTaskInstanceDto)
      );
  }

  protected void changeUserTaskIdleDuration(final ProcessInstanceEngineDto processInstanceDto,
                                            final Number durationInMs) {
    engineIntegrationExtension.getHistoricTaskInstances(processInstanceDto.getId())
      .forEach(
        historicUserTaskInstanceDto -> changeUserClaimStartTimestamp(durationInMs, historicUserTaskInstanceDto)
      );
  }

  private void changeUserClaimStartTimestamp(final Number durationInMs,
                                             final HistoricUserTaskInstanceDto historicUserTaskInstanceDto) {
    try {
      engineDatabaseExtension.changeUserTaskAssigneeClaimOperationTimestamp(
        historicUserTaskInstanceDto.getId(),
        historicUserTaskInstanceDto.getStartTime().plus(durationInMs.longValue(), ChronoUnit.MILLIS)
      );
    } catch (SQLException e) {
      throw new OptimizeIntegrationTestException(e);
    }
  }

  protected void changeUserTaskTotalDuration(final ProcessInstanceEngineDto processInstanceDto,
                                             final String userTaskKey,
                                             final Number durationInMs) {
    engineDatabaseExtension.changeFlowNodeTotalDuration(
      processInstanceDto.getId(),
      userTaskKey,
      durationInMs.longValue()
    );
  }

  protected void changeUserTaskTotalDuration(final ProcessInstanceEngineDto processInstanceDto,
                                             final Number durationInMs) {
    engineDatabaseExtension.changeAllFlowNodeTotalDurations(processInstanceDto.getId(), durationInMs.longValue());
  }

  protected void changeUserTaskWorkDuration(final ProcessInstanceEngineDto processInstanceDto,
                                            final Number durationInMs) {
    engineIntegrationExtension.getHistoricTaskInstances(processInstanceDto.getId())
      .forEach(
        historicUserTaskInstanceDto -> changeUserClaimEndTimestamp(
          historicUserTaskInstanceDto, durationInMs.longValue()
        )
      );
  }

  protected void changeUserTaskWorkDuration(final ProcessInstanceEngineDto processInstanceDto,
                                            final String userTaskKey,
                                            final Number durationInMs) {
    engineIntegrationExtension.getHistoricTaskInstances(processInstanceDto.getId(), userTaskKey)
      .forEach(
        historicUserTaskInstanceDto -> {
          if (historicUserTaskInstanceDto.getEndTime() != null) {
            changeUserClaimEndTimestamp(historicUserTaskInstanceDto, durationInMs.longValue());
          }
        }
      );
  }

  private void changeUserClaimEndTimestamp(final HistoricUserTaskInstanceDto userTaskInstance,
                                           final Number durationInMs) {
    try {
      if (userTaskInstance.getEndTime() != null) {
        engineDatabaseExtension.changeUserTaskAssigneeClaimOperationTimestamp(
          userTaskInstance.getId(),
          userTaskInstance.getEndTime().minus(durationInMs.longValue(), ChronoUnit.MILLIS)
        );
      }
    } catch (SQLException e) {
      throw new OptimizeIntegrationTestException(e);
    }
  }

  protected void changeUserTaskStartDate(final ProcessInstanceEngineDto processInstanceDto,
                                         final OffsetDateTime now,
                                         final String userTaskId,
                                         final Number offsetDurationInMs) {
    engineDatabaseExtension.changeFlowNodeStartDate(
      processInstanceDto.getId(), userTaskId, now.minus(offsetDurationInMs.longValue(), ChronoUnit.MILLIS)
    );
  }

  // this method is used for the parameterized tests
  @SuppressWarnings(UNUSED)
  protected static Stream<AggregateByDateUnit> staticAggregateByDateUnits() {
    return Arrays.stream(AggregateByDateUnit.values()).filter(g -> !g.equals(AggregateByDateUnit.AUTOMATIC));
  }

  protected void changeUserTaskClaimDate(final ProcessInstanceEngineDto processInstanceDto,
                                         final OffsetDateTime now,
                                         final String userTaskKey,
                                         final Number offsetDurationInMs) {
    engineIntegrationExtension.getHistoricTaskInstances(processInstanceDto.getId(), userTaskKey)
      .forEach(
        historicUserTaskInstanceDto -> {
          try {
            engineDatabaseExtension.changeUserTaskAssigneeClaimOperationTimestamp(
              historicUserTaskInstanceDto.getId(), now.minus(offsetDurationInMs.longValue(), ChronoUnit.MILLIS)
            );
          } catch (SQLException e) {
            throw new OptimizeIntegrationTestException(e);
          }
        }
      );
  }

  protected void assertCombinedDoubleVariableResultsAreInCorrectRanges(
    Double startRange,
    Double endRange,
    int expectedNumberOfBuckets,
    int resultSize,
    Map<String, AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>>> resultMap) {
    DecimalFormatSymbols decimalSymbols = new DecimalFormatSymbols(Locale.US);
    final DecimalFormat decimalFormat = new DecimalFormat("0.00", decimalSymbols);

    assertThat(resultMap).hasSize(resultSize);
    for (AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> result : resultMap.values()) {
      final List<MapResultEntryDto> resultData = result.getResult().getFirstMeasureData();
      assertThat(resultData.size()).isEqualTo(expectedNumberOfBuckets);
      assertThat(resultData.get(0).getKey()).isEqualTo(decimalFormat.format(startRange));
      assertThat(resultData.get(resultData.size() - 1).getKey()).isEqualTo(decimalFormat.format(endRange));
    }
  }

  protected String localDateTimeToString(ZonedDateTime time) {
    return embeddedOptimizeExtension.getDateTimeFormatter().format(time);
  }

  protected DurationFilterDataDto durationFilterData(final DurationUnit unit, final Long value,
                                                     final ComparisonOperator operator) {
    return DurationFilterDataDto.builder().unit(unit).value(value).operator(operator).build();
  }

  protected static Stream<List<ProcessFilterDto<?>>> viewLevelFilters() {
    return Stream.of(
      ProcessFilterBuilder
        .filter()
        .assignee()
        .id(DEFAULT_USERNAME)
        .filterLevel(FilterApplicationLevel.VIEW)
        .add()
        .buildList(),
      ProcessFilterBuilder
        .filter()
        .candidateGroups()
        .id(FIRST_CANDIDATE_GROUP_ID)
        .filterLevel(FilterApplicationLevel.VIEW)
        .add()
        .buildList(),
      ProcessFilterBuilder
        .filter()
        .flowNodeDuration()
        .flowNode(
          START_EVENT,
          DurationFilterDataDto.builder()
            .operator(ComparisonOperator.GREATER_THAN)
            .unit(DurationUnit.HOURS)
            .value(1L)
            .build()
        )
        .filterLevel(FilterApplicationLevel.VIEW)
        .add()
        .buildList(),
      ProcessFilterBuilder
        .filter().withOpenIncident().filterLevel(FilterApplicationLevel.VIEW).add().buildList(),
      ProcessFilterBuilder
        .filter().withResolvedIncident().filterLevel(FilterApplicationLevel.VIEW).add().buildList()
    );
  }

  protected static Triple<String, Double, String> createDefaultUserTriple(final Double value) {
    return Triple.of(DEFAULT_USERNAME, value, DEFAULT_FULLNAME);
  }

  protected static Triple<String, Double, String> createSecondUserTriple(final Double value) {
    return Triple.of(SECOND_USER, value, SECOND_USER_FULL_NAME);
  }

  protected static Triple<String, Double, String> createFirstGroupTriple(final Double value) {
    return Triple.of(FIRST_CANDIDATE_GROUP_ID, value, FIRST_CANDIDATE_GROUP_NAME);
  }

  protected static Triple<String, Double, String> createSecondGroupTriple(final Double value) {
    return Triple.of(SECOND_CANDIDATE_GROUP_ID, value, SECOND_CANDIDATE_GROUP_NAME);
  }

  protected static boolean isSingleFilterOfType(List<ProcessFilterDto<?>> processFilters, Class<?> filterType) {
    return processFilters.size() == 1 && processFilters.get(0).getClass().equals(filterType);
  }

}
