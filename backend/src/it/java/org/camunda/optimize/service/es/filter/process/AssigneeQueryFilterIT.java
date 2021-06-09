/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter.process;

import org.assertj.core.groups.Tuple;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.util.BpmnModels.USER_TASK_1;
import static org.camunda.optimize.util.BpmnModels.USER_TASK_2;
import static org.camunda.optimize.util.BpmnModels.getDoubleUserTaskDiagram;

public class AssigneeQueryFilterIT extends AbstractFilterIT {

  private static final String SECOND_USER = "secondUser";
  private static final String SECOND_USERS_PASSWORD = "fooPassword";
  private static final String THIRD_USER = "thirdUser";
  private static final String THIRD_USERS_PASSWORD = "fooPassword";

  @BeforeEach
  public void init() {
    engineIntegrationExtension.addUser(SECOND_USER, SECOND_USERS_PASSWORD);
    engineIntegrationExtension.grantAllAuthorizations(SECOND_USER);

    engineIntegrationExtension.addUser(THIRD_USER, THIRD_USERS_PASSWORD);
    engineIntegrationExtension.grantAllAuthorizations(THIRD_USER);
  }

  @Test
  public void filterByOneAssignee() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployUserTaskProcess();

    final ProcessInstanceEngineDto processInstance1 = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD, processInstance1.getId());

    final ProcessInstanceEngineDto processInstance2 = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(SECOND_USER, SECOND_USERS_PASSWORD, processInstance2.getId());

    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> assigneeFilter = ProcessFilterBuilder
      .filter()
      .assignee()
      .id(DEFAULT_USERNAME)
      .inOperator()
      .add()
      .buildList();

    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(
      processDefinition,
      assigneeFilter
    );

    // then
    assertThat(result.getData())
      .extracting(RawDataProcessInstanceDto::getProcessInstanceId)
      .containsExactly(processInstance1.getId());
  }

  @Test
  public void filterByMultipleAssignees_differentProcessInstances() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployUserTaskProcess();

    final ProcessInstanceEngineDto processInstance1 = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD, processInstance1.getId());

    final ProcessInstanceEngineDto processInstance2 = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(SECOND_USER, SECOND_USERS_PASSWORD, processInstance2.getId());

    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> assigneeFilter = ProcessFilterBuilder
      .filter()
      .assignee()
      .ids(DEFAULT_USERNAME, SECOND_USER)
      .inOperator()
      .add()
      .buildList();

    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(
      processDefinition,
      assigneeFilter
    );

    // then
    assertThat(result.getData())
      .extracting(RawDataProcessInstanceDto::getProcessInstanceId)
      .containsExactlyInAnyOrder(processInstance1.getId(), processInstance2.getId());
  }

  @Test
  public void filterByUnassigned() {
    // given
    final ProcessDefinitionEngineDto processDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(getDoubleUserTaskDiagram());

    final ProcessInstanceEngineDto processInstance1 = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(
      DEFAULT_USERNAME,
      DEFAULT_PASSWORD,
      processInstance1.getId()
    );
    engineIntegrationExtension.finishAllRunningUserTasks(
      DEFAULT_USERNAME,
      DEFAULT_PASSWORD,
      processInstance1.getId()
    );
    engineIntegrationExtension.finishAllRunningUserTasks(
      DEFAULT_USERNAME,
      DEFAULT_PASSWORD,
      processInstance1.getId()
    );

    final ProcessInstanceEngineDto processInstance2 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());

    importAllEngineEntitiesFromScratch();

    // when
    final List<ProcessFilterDto<?>> assigneeFilter = ProcessFilterBuilder
      .filter()
      .assignee()
      .id(null)
      .inOperator()
      .add()
      .buildList();

    final ReportResultResponseDto<List<RawDataProcessInstanceDto>> rawDataResult =
      evaluateReportWithFilter(processDefinition, assigneeFilter);
    final ReportResultResponseDto<List<MapResultEntryDto>> userTaskResult =
      evaluateUserTaskReportWithFilter(processDefinition, assigneeFilter);

    // then raw data report has both instances (because unassigned flowNodes exist on both)
    assertThat(rawDataResult.getData())
      .extracting(RawDataProcessInstanceDto::getProcessInstanceId)
      .containsExactlyInAnyOrder(processInstance1.getId(), processInstance2.getId());
    // and userTask report has only the instance that has not yet reached the second userTask
    // (because unassigned userTasks only exist on processInstance2)
    assertThat(userTaskResult.getInstanceCount()).isEqualTo(1L);
    assertThat(userTaskResult.getInstanceCountWithoutFilters()).isEqualTo(2L);
    assertThat(userTaskResult.getData())
      .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
      .containsExactlyInAnyOrder(Tuple.tuple(USER_TASK_1, 1.0), Tuple.tuple(USER_TASK_2, null));
  }

  @Test
  public void filterByUnassignedOrParticularAssignee() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployUserTaskProcess();

    final ProcessInstanceEngineDto processInstance1 = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(
      DEFAULT_USERNAME, DEFAULT_PASSWORD, processInstance1.getId()
    );

    final ProcessInstanceEngineDto processInstance2 = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(
      SECOND_USER, SECOND_USERS_PASSWORD, processInstance2.getId()
    );

    final ProcessInstanceEngineDto processInstance3 = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());

    importAllEngineEntitiesFromScratch();

    // when
    final List<ProcessFilterDto<?>> assigneeFilter = ProcessFilterBuilder
      .filter()
      .assignee()
      .ids(DEFAULT_USERNAME, null)
      .inOperator()
      .add()
      .buildList();

    final ReportResultResponseDto<List<RawDataProcessInstanceDto>> rawDataResult = evaluateReportWithFilter(
      processDefinition,
      assigneeFilter
    );
    final ReportResultResponseDto<List<MapResultEntryDto>> userTaskResult =
      evaluateUserTaskReportWithFilter(processDefinition, assigneeFilter);

    // then rawData report has all instances (because flowNodes with no assignee exist on both instances)
    assertThat(rawDataResult.getData())
      .extracting(RawDataProcessInstanceDto::getProcessInstanceId)
      .containsExactlyInAnyOrder(processInstance1.getId(), processInstance2.getId(), processInstance3.getId());
    // and userTask report does not have the instance where the only userTask is assigned to secondUser
    assertThat(userTaskResult.getInstanceCount()).isEqualTo(2L);
    assertThat(userTaskResult.getInstanceCountWithoutFilters()).isEqualTo(3L);
    assertThat(userTaskResult.getData())
      .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
      .containsExactlyInAnyOrder(Tuple.tuple(USER_TASK_1, 2.0));
  }

  @Test
  public void filterByNeitherUnassignedNorParticularAssignee() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployUserTaskProcess();

    final ProcessInstanceEngineDto assignedProcessInstance1 = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(
      DEFAULT_USERNAME,
      DEFAULT_PASSWORD,
      assignedProcessInstance1.getId()
    );

    final ProcessInstanceEngineDto assignedProcessInstance2 = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(
      SECOND_USER,
      SECOND_USERS_PASSWORD,
      assignedProcessInstance2.getId()
    );

    final ProcessInstanceEngineDto processInstance2 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());

    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> assigneeFilter = ProcessFilterBuilder
      .filter()
      .assignee()
      .ids(DEFAULT_USERNAME, null)
      .notInOperator()
      .add()
      .buildList();

    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(
      processDefinition,
      assigneeFilter
    );

    // then
    assertThat(result.getData())
      .extracting(RawDataProcessInstanceDto::getProcessInstanceId)
      .containsExactlyInAnyOrder(assignedProcessInstance2.getId());
  }

  @Test
  public void filterByMultipleAssigneeFiltersAcrossMultipleUserTasks() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksProcessDefinition();

    final ProcessInstanceEngineDto unexpectedProcessInstance = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(
      THIRD_USER,
      THIRD_USERS_PASSWORD,
      unexpectedProcessInstance.getId()
    );
    engineIntegrationExtension.finishAllRunningUserTasks(
      SECOND_USER,
      SECOND_USERS_PASSWORD,
      unexpectedProcessInstance.getId()
    );

    final ProcessInstanceEngineDto expectedProcessInstance = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(
      DEFAULT_USERNAME,
      DEFAULT_PASSWORD,
      expectedProcessInstance.getId()
    );

    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> assigneeFilter = ProcessFilterBuilder
      .filter()
      .assignee()
      .id(DEFAULT_USERNAME)
      .inOperator()
      .add()
      .assignee()
      .id(SECOND_USER)
      .notInOperator()
      .add()
      .buildList();

    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(
      processDefinition,
      assigneeFilter
    );

    // then
    assertThat(result.getData())
      .extracting(RawDataProcessInstanceDto::getProcessInstanceId)
      .containsExactlyInAnyOrder(expectedProcessInstance.getId());
  }

}
