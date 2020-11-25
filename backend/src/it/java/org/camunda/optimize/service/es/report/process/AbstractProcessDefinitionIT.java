/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process;

import com.google.common.collect.ImmutableMap;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.engine.HistoricUserTaskInstanceDto;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResultDto;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;
import static org.camunda.optimize.util.BpmnModels.getSingleUserTaskDiagram;

public class AbstractProcessDefinitionIT extends AbstractIT {

  protected static final String TEST_ACTIVITY = "testActivity";
  protected static final String BUSINESS_KEY = "aBusinessKey";
  protected static final String END_EVENT = "endEvent";
  protected static final String START_EVENT = "startEvent";
  protected static final String DEFAULT_VARIABLE_NAME = "foo";
  protected static final String DEFAULT_VARIABLE_VALUE = "bar";
  protected static final String USER_TASK_1 = "userTask1";
  protected static final String USER_TASK_2 = "userTask2";
  protected static final String USER_TASK_1_NAME = "userTask1Name";
  protected static final String USER_TASK_2_NAME = "userTask2Name";

  protected static final String FIRST_CANDIDATE_GROUP = "firstGroup";
  protected static final String SECOND_CANDIDATE_GROUP = "secondGroup";
  protected static final String SECOND_USER = "secondUser";
  protected static final String SECOND_USERS_PASSWORD = "secondUserPW";
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

  protected ProcessDefinitionEngineDto deploySimpleOneUserTasksDefinition() {
    return deploySimpleOneUserTasksDefinition(TEST_PROCESS, null);
  }

  protected ProcessDefinitionEngineDto deploySimpleOneUserTasksDefinition(String key) {
    return deploySimpleOneUserTasksDefinition(key, null);
  }

  protected ProcessDefinitionEngineDto deploySimpleOneUserTasksDefinition(String key, String tenantId) {
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(getSingleUserTaskDiagram(key), tenantId);
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

  protected ProcessDefinitionEngineDto deploySimpleServiceTaskProcessAndGetDefinition(String key) {
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
    deployedTenants.stream()
      .filter(Objects::nonNull)
      .forEach(tenantId -> engineIntegrationExtension.createTenant(tenantId));
    deployedTenants
      .forEach(tenant -> deployAndStartSimpleServiceTaskProcess(processKey, TEST_ACTIVITY, tenant));

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

  protected void startProcessInstanceAndModifyActivityDuration(final String definitionId,
                                                               final long activityDurationInMs) {
    final ProcessInstanceEngineDto thirdProcessInstance = engineIntegrationExtension.startProcessInstance(definitionId);
    engineDatabaseExtension.changeAllActivityDurations(thirdProcessInstance.getId(), activityDurationInMs);
  }

  protected void changeActivityDuration(final ProcessInstanceEngineDto processInstance,
                                        final Double durationInMs) {
    engineDatabaseExtension.changeAllActivityDurations(processInstance.getId(), durationInMs.longValue());
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
      engineDatabaseExtension.changeUserTaskAssigneeOperationTimestamp(
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
    engineDatabaseExtension.changeUserTaskDuration(processInstanceDto.getId(), userTaskKey, durationInMs.longValue());
  }

  protected void changeUserTaskTotalDuration(final ProcessInstanceEngineDto processInstanceDto,
                                             final Number durationInMs) {
    engineDatabaseExtension.changeUserTaskDuration(processInstanceDto.getId(), durationInMs.longValue());
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
        engineDatabaseExtension.changeUserTaskAssigneeOperationTimestamp(
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
    engineDatabaseExtension.changeUserTaskStartDate(
      processInstanceDto.getId(), userTaskId, now.minus(offsetDurationInMs.longValue(), ChronoUnit.MILLIS)
    );
  }

  // this method is used for the parameterized tests
  @SuppressWarnings("unused")
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
            engineDatabaseExtension.changeUserTaskAssigneeOperationTimestamp(
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
    Map<String, AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto>> resultMap) {
    DecimalFormatSymbols decimalSymbols = new DecimalFormatSymbols(Locale.US);
    final DecimalFormat decimalFormat = new DecimalFormat("0.00", decimalSymbols);

    assertThat(resultMap).hasSize(resultSize);
    for (AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> result : resultMap.values()) {
      final List<MapResultEntryDto> resultData = result.getResult().getData();
      assertThat(resultData.size()).isEqualTo(expectedNumberOfBuckets);
      assertThat(resultData.get(0).getKey()).isEqualTo(decimalFormat.format(startRange));
      assertThat(resultData.get(resultData.size() - 1).getKey()).isEqualTo(decimalFormat.format(endRange));
    }
  }

  protected String localDateTimeToString(ZonedDateTime time) {
    return embeddedOptimizeExtension.getDateTimeFormatter().format(time);
  }

}
