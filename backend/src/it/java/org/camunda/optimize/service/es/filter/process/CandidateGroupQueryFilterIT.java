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
import static org.camunda.optimize.util.BpmnModels.USER_TASK_1;
import static org.camunda.optimize.util.BpmnModels.USER_TASK_2;
import static org.camunda.optimize.util.BpmnModels.getDoubleUserTaskDiagram;

public class CandidateGroupQueryFilterIT extends AbstractFilterIT {

  private static final String SECOND_USER = "secondUser";
  private static final String SECOND_USERS_PASSWORD = "fooPassword";
  private static final String CANDIDATE_GROUP1 = "group1";
  private static final String CANDIDATE_GROUP2 = "group2";
  private static final String CANDIDATE_GROUP3 = "group3";

  @BeforeEach
  public void init() {
    engineIntegrationExtension.addUser(SECOND_USER, SECOND_USERS_PASSWORD);
    engineIntegrationExtension.grantAllAuthorizations(SECOND_USER);

    engineIntegrationExtension.createGroup(CANDIDATE_GROUP1);
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP2);
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP3);
  }

  @Test
  public void filterByOneCandidateGroup() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployUserTaskProcess();

    final ProcessInstanceEngineDto processInstance1 = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(CANDIDATE_GROUP1);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstance1.getId());

    final ProcessInstanceEngineDto processInstance2 = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(CANDIDATE_GROUP2);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstance2.getId());

    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> candidateGroupFilter = ProcessFilterBuilder
      .filter()
      .candidateGroups()
      .id(CANDIDATE_GROUP1)
      .inOperator()
      .add()
      .buildList();

    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(
      processDefinition,
      candidateGroupFilter
    );

    // then
    assertThat(result.getData())
      .extracting(RawDataProcessInstanceDto::getProcessInstanceId)
      .containsExactly(processInstance1.getId());
  }

  @Test
  public void filterByOneCandidateGroup_butMultiplePresentOnTask() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployUserTaskProcess();

    final ProcessInstanceEngineDto processInstance1 = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(CANDIDATE_GROUP1);
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(CANDIDATE_GROUP2);
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(CANDIDATE_GROUP3);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstance1.getId());

    final ProcessInstanceEngineDto processInstance2 = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(CANDIDATE_GROUP2);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstance2.getId());

    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> candidateGroupFilter = ProcessFilterBuilder
      .filter()
      .candidateGroups()
      .id(CANDIDATE_GROUP3)
      .inOperator()
      .add()
      .buildList();

    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(
      processDefinition,
      candidateGroupFilter
    );

    // then
    assertThat(result.getData())
      .extracting(RawDataProcessInstanceDto::getProcessInstanceId)
      .containsExactly(processInstance1.getId());
  }

  @Test
  public void filterByMultipleCandidateGroups_sameProcessInstances() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployUserTaskProcess();

    final ProcessInstanceEngineDto processInstance1 = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(CANDIDATE_GROUP1);
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(CANDIDATE_GROUP2);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstance1.getId());

    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> candidateGroupFilter = ProcessFilterBuilder
      .filter()
      .candidateGroups()
      .ids(CANDIDATE_GROUP1, CANDIDATE_GROUP2)
      .inOperator()
      .add()
      .buildList();

    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(
      processDefinition,
      candidateGroupFilter
    );
    // then
    assertThat(result.getData())
      .extracting(RawDataProcessInstanceDto::getProcessInstanceId)
      .containsExactlyInAnyOrder(processInstance1.getId());
  }

  @Test
  public void filterByMultipleCandidateGroups_differentProcessInstances() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployUserTaskProcess();

    final ProcessInstanceEngineDto processInstance1 = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(CANDIDATE_GROUP1);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstance1.getId());

    final ProcessInstanceEngineDto processInstance2 = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(CANDIDATE_GROUP2);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstance2.getId());

    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> candidateGroupFilter = ProcessFilterBuilder
      .filter()
      .candidateGroups()
      .ids(CANDIDATE_GROUP1, CANDIDATE_GROUP2)
      .inOperator()
      .add()
      .buildList();

    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(
      processDefinition,
      candidateGroupFilter
    );

    // then
    assertThat(result.getData())
      .extracting(RawDataProcessInstanceDto::getProcessInstanceId)
      .containsExactlyInAnyOrder(processInstance1.getId(), processInstance2.getId());
  }

  @Test
  public void filterByUnassignedCandidateGroup() {
    // given
    final ProcessDefinitionEngineDto processDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(getDoubleUserTaskDiagram());

    final ProcessInstanceEngineDto processInstance1 = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(CANDIDATE_GROUP1);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstance1.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(CANDIDATE_GROUP1);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstance1.getId());

    final ProcessInstanceEngineDto processInstance2 = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());

    importAllEngineEntitiesFromScratch();

    // when
    final List<ProcessFilterDto<?>> candidateGroupFilter = ProcessFilterBuilder
      .filter()
      .candidateGroups()
      .id(null)
      .inOperator()
      .add()
      .buildList();

    final ReportResultResponseDto<List<RawDataProcessInstanceDto>> rawDataResult =
      evaluateReportWithFilter(processDefinition, candidateGroupFilter);
    final ReportResultResponseDto<List<MapResultEntryDto>> userTaskResult =
      evaluateUserTaskReportWithFilter(processDefinition, candidateGroupFilter);

    // then raw data report has both instances (because flowNodes without candidateGroups exist on both)
    assertThat(rawDataResult.getData())
      .extracting(RawDataProcessInstanceDto::getProcessInstanceId)
      .containsExactlyInAnyOrder(processInstance1.getId(), processInstance2.getId());
    // and userTask report has only the instance that has not yet reached the second userTask
    // (because userTasks without candidateGroups only exist on processInstance2)
    assertThat(userTaskResult.getInstanceCount()).isEqualTo(1L);
    assertThat(userTaskResult.getInstanceCountWithoutFilters()).isEqualTo(2L);
    assertThat(userTaskResult.getData())
      .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
      .containsExactlyInAnyOrder(Tuple.tuple(USER_TASK_1, 1.0), Tuple.tuple(USER_TASK_2, null));
  }

  @Test
  public void filterByUnassignedOrParticularCandidateGroup() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployUserTaskProcess();

    final ProcessInstanceEngineDto processInstance1 = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(CANDIDATE_GROUP1);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstance1.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(CANDIDATE_GROUP1);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstance1.getId());

    final ProcessInstanceEngineDto processInstance2 = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(CANDIDATE_GROUP2);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstance2.getId());

    final ProcessInstanceEngineDto processInstance3 = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());

    importAllEngineEntitiesFromScratch();

    // when
    final List<ProcessFilterDto<?>> candidateGroupFilter = ProcessFilterBuilder
      .filter()
      .candidateGroups()
      .ids(CANDIDATE_GROUP1, null)
      .inOperator()
      .add()
      .buildList();

    final ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(
      processDefinition,
      candidateGroupFilter
    );
    final ReportResultResponseDto<List<MapResultEntryDto>> userTaskResult =
      evaluateUserTaskReportWithFilter(processDefinition, candidateGroupFilter);

    // then rawData report has all instances (because flowNodes with no candidateGroup exist on both instances)
    assertThat(result.getData())
      .extracting(RawDataProcessInstanceDto::getProcessInstanceId)
      .containsExactlyInAnyOrder(processInstance1.getId(), processInstance2.getId(), processInstance3.getId());
    // and userTask report only has 2 instances because processInstance3 only has a userTask with a candidateGroup
    // that does not match the filter
    assertThat(userTaskResult.getInstanceCount()).isEqualTo(2L);
    assertThat(userTaskResult.getInstanceCountWithoutFilters()).isEqualTo(3L);
    assertThat(userTaskResult.getData())
      .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
      .containsExactlyInAnyOrder(Tuple.tuple(USER_TASK_1, 2.0));
  }

  @Test
  public void filterByNeitherUnassignedNorParticularCandidateGroup() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployUserTaskProcess();

    final ProcessInstanceEngineDto assignedProcessInstance1 = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(CANDIDATE_GROUP1);
    engineIntegrationExtension.finishAllRunningUserTasks(assignedProcessInstance1.getId());

    final ProcessInstanceEngineDto assignedProcessInstance2 = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(CANDIDATE_GROUP2);
    engineIntegrationExtension.finishAllRunningUserTasks(assignedProcessInstance2.getId());

    final ProcessInstanceEngineDto processInstance2 = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());

    importAllEngineEntitiesFromScratch();

    // when
    final List<ProcessFilterDto<?>> candidateGroupFilter = ProcessFilterBuilder
      .filter()
      .candidateGroups()
      .ids(CANDIDATE_GROUP1, null)
      .notInOperator()
      .add()
      .buildList();

    final ReportResultResponseDto<List<RawDataProcessInstanceDto>> result =
      evaluateReportWithFilter(processDefinition, candidateGroupFilter);

    // then
    assertThat(result.getData())
      .extracting(RawDataProcessInstanceDto::getProcessInstanceId)
      .containsExactlyInAnyOrder(assignedProcessInstance2.getId());
  }

  @Test
  public void filterByMultipleCandidateGroupFiltersOnSameUserTask() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployUserTaskProcess();

    final ProcessInstanceEngineDto processInstance1 = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(CANDIDATE_GROUP1);
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(CANDIDATE_GROUP2);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstance1.getId());

    final ProcessInstanceEngineDto processInstance2 = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(CANDIDATE_GROUP1);
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(CANDIDATE_GROUP2);
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(CANDIDATE_GROUP3);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstance1.getId());

    final ProcessInstanceEngineDto unexpectedProcessInstance = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(CANDIDATE_GROUP3);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstance1.getId());

    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> candidateGroupFilter = ProcessFilterBuilder
      .filter()
      .candidateGroups()
      .ids(CANDIDATE_GROUP1, CANDIDATE_GROUP2)
      .inOperator()
      .add()
      .candidateGroups()
      .id(CANDIDATE_GROUP3)
      .notInOperator()
      .add()
      .buildList();

    final ReportResultResponseDto<List<RawDataProcessInstanceDto>> rawDataResult = evaluateReportWithFilter(
      processDefinition,
      candidateGroupFilter
    );
    final ReportResultResponseDto<List<MapResultEntryDto>> userTaskResult =
      evaluateUserTaskReportWithFilter(processDefinition, candidateGroupFilter);

    // then raw data report has both instances (because flowNodes without candidateGroups exist on both)
    assertThat(rawDataResult.getData())
      .extracting(RawDataProcessInstanceDto::getProcessInstanceId)
      .containsExactlyInAnyOrder(processInstance1.getId(), processInstance2.getId());
    // and userTask report has only the instance that has not yet reached the second userTask
    // (because userTasks without candidateGroups only exist on processInstance2)
    assertThat(userTaskResult.getInstanceCount()).isEqualTo(1L);
    assertThat(userTaskResult.getInstanceCountWithoutFilters()).isEqualTo(3L);
    assertThat(userTaskResult.getData())
      .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
      .containsExactlyInAnyOrder(Tuple.tuple(USER_TASK_1, 1.0));
  }

  @Test
  public void filterByMultipleCandidateGroupFiltersOnAcrossDifferentUserTasks() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksProcessDefinition();

    final ProcessInstanceEngineDto expectedProcessInstance = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(CANDIDATE_GROUP1);
    engineIntegrationExtension.finishAllRunningUserTasks(expectedProcessInstance.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(CANDIDATE_GROUP2);
    engineIntegrationExtension.finishAllRunningUserTasks(expectedProcessInstance.getId());

    final ProcessInstanceEngineDto unexpectedProcessInstance = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(CANDIDATE_GROUP3);
    engineIntegrationExtension.finishAllRunningUserTasks(unexpectedProcessInstance.getId());

    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> candidateGroupFilter = ProcessFilterBuilder
      .filter()
      .candidateGroups()
      .ids(CANDIDATE_GROUP1, CANDIDATE_GROUP2)
      .inOperator()
      .add()
      .candidateGroups()
      .id(CANDIDATE_GROUP3)
      .notInOperator()
      .add()
      .buildList();

    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(
      processDefinition,
      candidateGroupFilter
    );

    // then
    assertThat(result.getData())
      .extracting(RawDataProcessInstanceDto::getProcessInstanceId)
      .containsExactlyInAnyOrder(expectedProcessInstance.getId());
  }

}
