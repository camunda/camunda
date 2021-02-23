/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter.process;

import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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

    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(processDefinition, candidateGroupFilter);

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

    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(processDefinition, candidateGroupFilter);

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

    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(processDefinition, candidateGroupFilter);
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

    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(processDefinition, candidateGroupFilter);

    // then
    assertThat(result.getData())
      .extracting(RawDataProcessInstanceDto::getProcessInstanceId)
      .containsExactlyInAnyOrder(processInstance1.getId(), processInstance2.getId());
  }

  @Test
  public void filterByUnassignedCandidateGroup() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployUserTaskProcess();

    final ProcessInstanceEngineDto assignedProcessInstance = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(CANDIDATE_GROUP1);
    engineIntegrationExtension.finishAllRunningUserTasks(assignedProcessInstance.getId());

    final ProcessInstanceEngineDto unassignedProcessInstance = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());

    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> candidateGroupFilter = ProcessFilterBuilder
      .filter()
      .candidateGroups()
      .id(null)
      .inOperator()
      .add()
      .buildList();

    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(processDefinition, candidateGroupFilter);

    // then
    assertThat(result.getData())
      .extracting(RawDataProcessInstanceDto::getProcessInstanceId)
      .containsExactly(unassignedProcessInstance.getId());
  }

  @Test
  public void filterByUnassignedOrParticularCandidateGroup() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployUserTaskProcess();

    final ProcessInstanceEngineDto assignedProcessInstance = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(CANDIDATE_GROUP1);
    engineIntegrationExtension.finishAllRunningUserTasks(assignedProcessInstance.getId());

    final ProcessInstanceEngineDto differentAssigneeProcessInstance = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(CANDIDATE_GROUP2);
    engineIntegrationExtension.finishAllRunningUserTasks(differentAssigneeProcessInstance.getId());

    final ProcessInstanceEngineDto unassignedProcessInstance = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());

    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> candidateGroupFilter = ProcessFilterBuilder
      .filter()
      .candidateGroups()
      .ids(CANDIDATE_GROUP1, null)
      .inOperator()
      .add()
      .buildList();

    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(processDefinition, candidateGroupFilter);

    // then
    assertThat(result.getData())
      .extracting(RawDataProcessInstanceDto::getProcessInstanceId)
      .containsExactlyInAnyOrder(assignedProcessInstance.getId(), unassignedProcessInstance.getId());
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

    final ProcessInstanceEngineDto unassignedProcessInstance = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());

    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> candidateGroupFilter = ProcessFilterBuilder
      .filter()
      .candidateGroups()
      .ids(CANDIDATE_GROUP1, null)
      .notInOperator()
      .add()
      .buildList();

    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(processDefinition, candidateGroupFilter);

    // then
    assertThat(result.getData())
      .extracting(RawDataProcessInstanceDto::getProcessInstanceId)
      .containsExactlyInAnyOrder(assignedProcessInstance2.getId());
  }

  @Test
  public void filterByMultipleCandidateGroupFiltersOnSameUserTask() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployUserTaskProcess();

    final ProcessInstanceEngineDto expectedProcessInstance = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(CANDIDATE_GROUP1);
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(CANDIDATE_GROUP2);
    engineIntegrationExtension.finishAllRunningUserTasks(expectedProcessInstance.getId());

    final ProcessInstanceEngineDto unexpectedProcessInstance = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(CANDIDATE_GROUP1);
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(CANDIDATE_GROUP2);
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(CANDIDATE_GROUP3);
    engineIntegrationExtension.finishAllRunningUserTasks(expectedProcessInstance.getId());

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

    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(processDefinition, candidateGroupFilter);

    // then
    assertThat(result.getData())
      .extracting(RawDataProcessInstanceDto::getProcessInstanceId)
      .containsExactlyInAnyOrder(expectedProcessInstance.getId());
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

    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(processDefinition, candidateGroupFilter);

    // then
    assertThat(result.getData())
      .extracting(RawDataProcessInstanceDto::getProcessInstanceId)
      .containsExactlyInAnyOrder(expectedProcessInstance.getId());
  }

}
