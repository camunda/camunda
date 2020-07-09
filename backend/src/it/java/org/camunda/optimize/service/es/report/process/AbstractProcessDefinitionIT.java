/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process;

import com.google.common.collect.ImmutableMap;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.engine.HistoricUserTaskInstanceDto;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResultDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.extension.EngineDatabaseExtension;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.ws.rs.core.Response;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Duration;
import java.time.OffsetDateTime;
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

public class AbstractProcessDefinitionIT extends AbstractIT {

  protected static final String TEST_ACTIVITY = "testActivity";
  protected static final String BUSINESS_KEY = "aBusinessKey";
  protected static final String END_EVENT = "endEvent";
  protected static final String START_EVENT = "startEvent";
  protected static final String USER_TASK = "userTask";
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
  public static final String TEST_PROCESS = "aProcess";

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

  @RegisterExtension
  @Order(4)
  protected EngineDatabaseExtension engineDatabaseExtension =
    new EngineDatabaseExtension(engineIntegrationExtension.getEngineName());

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
    BpmnModelInstance processModel = Bpmn.createExecutableProcess(TEST_PROCESS)
      .name("aProcessName")
      .startEvent(START_EVENT)
      .endEvent(END_EVENT)
      .done();
    return engineIntegrationExtension.deployAndStartProcessWithVariables(
      processModel,
      variables,
      BUSINESS_KEY,
      tenantId
    );
  }

  protected ProcessInstanceEngineDto deployAndStartSimpleUserTaskProcess() {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .startEvent(START_EVENT)
      .userTask(USER_TASK)
      .endEvent(END_EVENT)
      .done();
    return engineIntegrationExtension.deployAndStartProcess(processModel);
  }

  protected ProcessDefinitionEngineDto deploySimpleOneUserTasksDefinition() {
    return deploySimpleOneUserTasksDefinition("aProcess", null);
  }

  protected ProcessDefinitionEngineDto deploySimpleOneUserTasksDefinition(String key, String tenantId) {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess(key)
      .startEvent(START_EVENT)
      .userTask(USER_TASK)
      .endEvent(END_EVENT)
      .done();
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(modelInstance, tenantId);
  }

  protected ProcessInstanceEngineDto deployAndStartSimpleServiceTaskProcess() {
    return deployAndStartSimpleServiceTaskProcess(TEST_ACTIVITY);
  }

  protected ProcessInstanceEngineDto deployAndStartSimpleServiceTaskProcess(String activityId) {
    return deployAndStartSimpleServiceTaskProcess("aProcess", activityId, null);
  }

  protected ProcessInstanceEngineDto deployAndStartSimpleServiceTaskProcess(String key,
                                                                            String activityId,
                                                                            String tenantId) {
    BpmnModelInstance processModel = createSimpleServiceTaskModelInstance(key, activityId);
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
    BpmnModelInstance processModel = createSimpleServiceTaskModelInstance(TEST_PROCESS, "anActivityId");
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(processModel);
  }

  protected ProcessDefinitionEngineDto deploySimpleGatewayProcessDefinition() {
    // @formatter:off
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess()
      .startEvent("startEvent")
      .exclusiveGateway("splittingGateway")
        .name("Should we go to task 1?")
        .condition("yes", "${goToTask1}")
        .serviceTask("task1")
          .camundaExpression("${true}")
      .exclusiveGateway("mergeGateway")
        .endEvent("endEvent")
      .moveToNode("splittingGateway")
        .condition("no", "${!goToTask1}")
        .serviceTask("task2")
          .camundaExpression("${true}")
        .connectTo("mergeGateway")
      .done();
    // @formatter:on
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(modelInstance);
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

  protected List<ProcessInstanceEngineDto> startAndEndProcessInstancesWithGivenRuntime(
    final int numberOfInstances,
    final Duration instanceRuntime,
    final OffsetDateTime startTimeOfFirstInstance) throws SQLException {
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

  protected String createNewReport(ProcessReportDataDto processReportDataDto) {
    SingleProcessReportDefinitionDto singleProcessReportDefinitionDto = new SingleProcessReportDefinitionDto();
    singleProcessReportDefinitionDto.setData(processReportDataDto);
    singleProcessReportDefinitionDto.setLastModifier("something");
    singleProcessReportDefinitionDto.setName("something");
    singleProcessReportDefinitionDto.setCreated(OffsetDateTime.now());
    singleProcessReportDefinitionDto.setLastModified(OffsetDateTime.now());
    singleProcessReportDefinitionDto.setOwner("something");
    return createNewReport(singleProcessReportDefinitionDto);
  }

  protected String createNewReport(SingleProcessReportDefinitionDto singleProcessReportDefinitionDto) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest(singleProcessReportDefinitionDto)
      .execute(IdDto.class, Response.Status.OK.getStatusCode())
      .getId();
  }

  private BpmnModelInstance createSimpleServiceTaskModelInstance(final String key,
                                                                 final String activityId) {
    // @formatter:off
    return Bpmn.createExecutableProcess(key)
      .name("aProcessName")
      .startEvent(START_EVENT)
      .serviceTask(activityId)
      .camundaExpression("${true}")
      .endEvent(END_EVENT)
      .done();
    // @formatter:on
  }

  protected void changeUserTaskIdleDuration(final ProcessInstanceEngineDto processInstanceDto,
                                            final String userTaskKey,
                                            final double durationInMs) {
    engineIntegrationExtension.getHistoricTaskInstances(processInstanceDto.getId(), userTaskKey)
      .forEach(
        historicUserTaskInstanceDto ->
          changeUserClaimStartTimestamp(
            durationInMs,
            historicUserTaskInstanceDto
          )
      );
  }

  protected void changeUserTaskIdleDuration(final ProcessInstanceEngineDto processInstanceDto,
                                            final double durationInMs) {
    engineIntegrationExtension.getHistoricTaskInstances(processInstanceDto.getId())
      .forEach(
        historicUserTaskInstanceDto ->
          changeUserClaimStartTimestamp(
            durationInMs,
            historicUserTaskInstanceDto
          )
      );
  }

  private void changeUserClaimStartTimestamp(final Double durationInMs,
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
                                             final Double durationInMs) {
    try {
      engineDatabaseExtension.changeUserTaskDuration(processInstanceDto.getId(), userTaskKey, durationInMs.longValue());
    } catch (SQLException e) {
      throw new OptimizeIntegrationTestException(e);
    }
  }

  protected void changeUserTaskTotalDuration(final ProcessInstanceEngineDto processInstanceDto,
                                             final Double durationInMs) {
    try {
      engineDatabaseExtension.changeUserTaskDuration(processInstanceDto.getId(), durationInMs.longValue());
    } catch (SQLException e) {
      throw new OptimizeIntegrationTestException(e);
    }
  }

  protected void changeUserTaskWorkDuration(final ProcessInstanceEngineDto processInstanceDto,
                                            final Double durationInMs) {
    engineIntegrationExtension.getHistoricTaskInstances(processInstanceDto.getId())
      .forEach(
        historicUserTaskInstanceDto ->
          changeUserClaimEndTimestamp(
            durationInMs,
            historicUserTaskInstanceDto
          )
      );
  }

  protected void changeUserTaskWorkDuration(final ProcessInstanceEngineDto processInstanceDto,
                                            final String userTaskKey,
                                            final Double durationInMs) {
    engineIntegrationExtension.getHistoricTaskInstances(processInstanceDto.getId(), userTaskKey)
      .forEach(
        historicUserTaskInstanceDto -> {
          if (historicUserTaskInstanceDto.getEndTime() != null) {
            changeUserClaimEndTimestamp(
              durationInMs,
              historicUserTaskInstanceDto
            );
          }
        }
      );
  }

  private void changeUserClaimEndTimestamp(final Double durationInMs,
                                           final HistoricUserTaskInstanceDto historicUserTaskInstanceDto) {
    try {
      if (historicUserTaskInstanceDto.getEndTime() != null) {
        engineDatabaseExtension.changeUserTaskAssigneeOperationTimestamp(
          historicUserTaskInstanceDto.getId(),
          historicUserTaskInstanceDto.getEndTime().minus(durationInMs.longValue(), ChronoUnit.MILLIS)
        );
      }
    } catch (SQLException e) {
      throw new OptimizeIntegrationTestException(e);
    }
  }

  protected void changeUserTaskStartDate(final ProcessInstanceEngineDto processInstanceDto,
                                         final OffsetDateTime now,
                                         final String userTaskId,
                                         final Double offsetDuration) {
    engineDatabaseExtension.changeUserTaskStartDate(
      processInstanceDto.getId(),
      userTaskId,
      now.minus(offsetDuration.longValue(), ChronoUnit.MILLIS)
    );
  }

  // this method is used for the parameterized tests
  @SuppressWarnings("unused")
  protected static Stream<GroupByDateUnit> staticGroupByDateUnits() {
    return Arrays.stream(GroupByDateUnit.values()).filter(g -> !g.equals(GroupByDateUnit.AUTOMATIC));
  }

  protected void changeUserTaskClaimDate(final ProcessInstanceEngineDto processInstanceDto,
                                         final OffsetDateTime now,
                                         final String userTaskKey,
                                         final Double offsetDuration) {

    engineIntegrationExtension.getHistoricTaskInstances(processInstanceDto.getId(), userTaskKey)
      .forEach(
        historicUserTaskInstanceDto ->
        {
          try {
            engineDatabaseExtension.changeUserTaskAssigneeOperationTimestamp(
              historicUserTaskInstanceDto.getId(),
              now.minus(offsetDuration.longValue(), ChronoUnit.MILLIS)
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

}
