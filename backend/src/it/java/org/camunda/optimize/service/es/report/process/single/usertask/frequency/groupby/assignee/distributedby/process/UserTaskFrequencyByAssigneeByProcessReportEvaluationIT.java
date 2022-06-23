/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.process.single.usertask.frequency.groupby.assignee.distributedby.process;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.DistributedByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.HyperMapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.measure.MeasureResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.util.IdGenerator;
import org.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_PASSWORD;
import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_USERNAME;
import static org.camunda.optimize.service.es.report.command.modules.distributed_by.process.identity.ProcessDistributedByIdentity.DISTRIBUTE_BY_IDENTITY_MISSING_KEY;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_FULLNAME;
import static org.camunda.optimize.service.util.ProcessReportDataType.USER_TASK_FREQ_GROUP_BY_ASSIGNEE_BY_PROCESS;
import static org.camunda.optimize.util.BpmnModels.getFourUserTaskDiagram;
import static org.camunda.optimize.util.BpmnModels.getSingleUserTaskDiagram;

public class UserTaskFrequencyByAssigneeByProcessReportEvaluationIT extends AbstractIT {

  private static final String SECOND_USER = "secondUser";
  private static final String SECOND_USER_FIRST_NAME = "the";
  private static final String SECOND_USER_LAST_NAME = "other";
  private static final String SECOND_USER_FULL_NAME = SECOND_USER_FIRST_NAME + " " + SECOND_USER_LAST_NAME;

  @BeforeEach
  public void init() {
    // create second user
    engineIntegrationExtension.addUser(SECOND_USER, SECOND_USER_FIRST_NAME, SECOND_USER_LAST_NAME);
    engineIntegrationExtension.grantAllAuthorizations(SECOND_USER);
  }

  @Test
  public void reportEvaluationWithSingleProcessDefinitionSource() {
    // given
    final ProcessDefinitionEngineDto process = deployFourUserTasksDefinition("process");
    final ProcessInstanceEngineDto instance = engineIntegrationExtension.startProcessInstance(process.getId());
    finishUserTask1AWithDefaultAndTaskB2WithSecondUser(instance);
    importAllEngineEntitiesFromScratch();
    final String processDisplayName = "processDisplayName";
    final String processIdentifier = IdGenerator.getNextId();
    ReportDataDefinitionDto definition =
      new ReportDataDefinitionDto(processIdentifier, process.getKey(), processDisplayName);

    // when
    final ProcessReportDataDto reportData = createReport(Collections.singletonList(definition));
    final AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(process.getKey());
    assertThat(resultReportDataDto.getDefinitionVersions()).containsExactly(definition.getVersions().get(0));
    assertThat(resultReportDataDto.getView()).isNotNull();
    assertThat(resultReportDataDto.getView().getEntity()).isEqualTo(ProcessViewEntity.USER_TASK);
    assertThat(resultReportDataDto.getView().getFirstProperty()).isEqualTo(ViewProperty.FREQUENCY);
    assertThat(resultReportDataDto.getGroupBy()).isNotNull();
    assertThat(resultReportDataDto.getGroupBy().getType()).isEqualTo(ProcessGroupByType.ASSIGNEE);
    assertThat(resultReportDataDto.getGroupBy().getValue()).isNull();
    assertThat(resultReportDataDto.getDistributedBy().getType()).isEqualTo(DistributedByType.PROCESS);

    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = evaluationResponse.getResult();
    assertThat(result.getInstanceCount()).isEqualTo(1);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(1);
    assertThat(result.getMeasures()).hasSize(1)
      .extracting(MeasureResponseDto::getData)
      .containsExactly(List.of(
        createHyperMapResult(
          DEFAULT_USERNAME,
          DEFAULT_FULLNAME,
          new MapResultEntryDto(processIdentifier, 2.0, processDisplayName)
        ),
        createHyperMapResult(
          SECOND_USER,
          SECOND_USER_FULL_NAME,
          new MapResultEntryDto(processIdentifier, 2.0, processDisplayName)
        )
      ));
  }

  @Test
  public void reportEvaluationWithSingleProcessDefinitionSourceWithUnassignedUser() {
    // given
    final ProcessDefinitionEngineDto process = deployFourUserTasksDefinition("process");
    final ProcessInstanceEngineDto instance = engineIntegrationExtension.startProcessInstance(process.getId());
    finishUserTask1AWithDefaultAndLeaveTasks2BUnassigned(instance);
    importAllEngineEntitiesFromScratch();
    final String processDisplayName = "processDisplayName";
    final String processIdentifier = IdGenerator.getNextId();
    ReportDataDefinitionDto definition =
      new ReportDataDefinitionDto(processIdentifier, process.getKey(), processDisplayName);

    // when
    final ProcessReportDataDto reportData = createReport(Collections.singletonList(definition));
    final AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = evaluationResponse.getResult();
    assertThat(result.getInstanceCount()).isEqualTo(1);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(1);
    assertThat(result.getMeasures()).hasSize(1)
      .extracting(MeasureResponseDto::getData)
      .containsExactly(List.of(
        createHyperMapResult(
          DEFAULT_USERNAME,
          DEFAULT_FULLNAME,
          new MapResultEntryDto(processIdentifier, 2.0, processDisplayName)
        ),
        createHyperMapResult(
          DISTRIBUTE_BY_IDENTITY_MISSING_KEY,
          getLocalisedUnassignedLabel(),
          new MapResultEntryDto(processIdentifier, 2.0, processDisplayName)
        )
      ));
  }

  @Test
  public void reportEvaluationWithSingleProcessDefinitionSourceWithAllInstancesRemovedByFilter() {
    // given
    final ProcessDefinitionEngineDto process = deployFourUserTasksDefinition("first");
    final ProcessInstanceEngineDto instance = engineIntegrationExtension.startProcessInstance(process.getId());
    finishUserTask1AWithDefaultAndTaskB2WithSecondUser(instance);
    importAllEngineEntitiesFromScratch();
    final String processDisplayName = "processDisplayName";
    final String processIdentifier = IdGenerator.getNextId();
    ReportDataDefinitionDto definition =
      new ReportDataDefinitionDto(processIdentifier, process.getKey(), processDisplayName);

    // when
    final ProcessReportDataDto reportData = createReport(Collections.singletonList(definition));
    reportData.setFilter(ProcessFilterBuilder.filter().canceledInstancesOnly().add().buildList());
    final AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = evaluationResponse.getResult();
    assertThat(result.getInstanceCount()).isZero();
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(1);
    assertThat(result.getMeasures()).hasSize(1)
      .extracting(MeasureResponseDto::getData)
      .containsExactly(Collections.emptyList());
  }

  @Test
  public void reportEvaluationWithMultipleProcessDefinitionSources() {
    // given
    final ProcessDefinitionEngineDto firstProcess = deployFourUserTasksDefinition("first");
    final ProcessInstanceEngineDto firstInstance =
      engineIntegrationExtension.startProcessInstance(firstProcess.getId());
    finishUserTask1AWithDefaultAndTaskB2WithSecondUser(firstInstance);
    final ProcessInstanceEngineDto secondInstance =
      engineIntegrationExtension.deployAndStartProcess(getSingleUserTaskDiagram("second"));
    engineIntegrationExtension.finishAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD, secondInstance.getId());
    importAllEngineEntitiesFromScratch();
    final String firstDisplayName = "firstName";
    final String secondDisplayName = "secondName";
    final String firstIdentifier = "first";
    final String secondIdentifier = "second";
    ReportDataDefinitionDto firstDefinition =
      new ReportDataDefinitionDto(firstIdentifier, firstProcess.getKey(), firstDisplayName);
    ReportDataDefinitionDto secondDefinition =
      new ReportDataDefinitionDto(secondIdentifier, secondInstance.getProcessDefinitionKey(), secondDisplayName);

    // when
    final ProcessReportDataDto reportData = createReport(List.of(firstDefinition, secondDefinition));
    final AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = evaluationResponse.getResult();
    assertThat(result.getInstanceCount()).isEqualTo(2);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(2);
    assertThat(result.getMeasures()).hasSize(1)
      .extracting(MeasureResponseDto::getData)
      .containsExactly(List.of(
        createHyperMapResult(
          DEFAULT_USERNAME,
          DEFAULT_FULLNAME,
          new MapResultEntryDto(firstIdentifier, 2.0, firstDisplayName),
          new MapResultEntryDto(secondIdentifier, 1.0, secondDisplayName)
        ),
        createHyperMapResult(
          SECOND_USER,
          SECOND_USER_FULL_NAME,
          new MapResultEntryDto(firstIdentifier, 2.0, firstDisplayName),
          new MapResultEntryDto(secondIdentifier, null, secondDisplayName)
        )
      ));
  }

  @Test
  public void reportEvaluationWithMultipleProcessDefinitionSourcesAndOverlappingInstances() {
    // given
    final ProcessDefinitionEngineDto v1Process = deployFourUserTasksDefinition("definition");
    final ProcessInstanceEngineDto v1Instance = engineIntegrationExtension.startProcessInstance(v1Process.getId());
    finishUserTask1AWithDefaultAndTaskB2WithSecondUser(v1Instance);
    final ProcessInstanceEngineDto v2Instance =
      engineIntegrationExtension.deployAndStartProcess(getSingleUserTaskDiagram("definition"));
    engineIntegrationExtension.finishAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD, v2Instance.getId());
    importAllEngineEntitiesFromScratch();
    final String v1displayName = "v1";
    final String allVersionsDisplayName = "all";
    final String v1Identifier = "v1Identifier";
    final String allVersionsIdentifier = "allIdentifier";
    ReportDataDefinitionDto v1definition =
      new ReportDataDefinitionDto(v1Identifier, v1Process.getKey(), v1displayName);
    v1definition.setVersion("1");
    ReportDataDefinitionDto allVersionsDefinition = new ReportDataDefinitionDto(
      allVersionsIdentifier, v2Instance.getProcessDefinitionKey(), allVersionsDisplayName);
    allVersionsDefinition.setVersion(ALL_VERSIONS);

    // when
    final ProcessReportDataDto reportData = createReport(List.of(v1definition, allVersionsDefinition));
    final AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = evaluationResponse.getResult();
    assertThat(result.getInstanceCount()).isEqualTo(2);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(2);
    assertThat(result.getMeasures()).hasSize(1)
      .extracting(MeasureResponseDto::getData)
      .containsExactly(List.of(
        createHyperMapResult(
          DEFAULT_USERNAME,
          DEFAULT_FULLNAME,
          new MapResultEntryDto(allVersionsIdentifier, 3.0, allVersionsDisplayName),
          new MapResultEntryDto(v1Identifier, 2.0, v1displayName)
        ),
        createHyperMapResult(
          SECOND_USER,
          SECOND_USER_FULL_NAME,
          new MapResultEntryDto(allVersionsIdentifier, 2.0, allVersionsDisplayName),
          new MapResultEntryDto(v1Identifier, 2.0, v1displayName)
        )
      ));
  }

  private ProcessDefinitionEngineDto deployFourUserTasksDefinition(final String aProcessName) {
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(getFourUserTaskDiagram(aProcessName));
  }

  private void finishUserTask1AWithDefaultAndTaskB2WithSecondUser(final ProcessInstanceEngineDto processInstanceDto) {
    // finish user task 1 and A with default user
    engineIntegrationExtension.finishAllRunningUserTasks(
      DEFAULT_USERNAME,
      DEFAULT_PASSWORD,
      processInstanceDto.getId()
    );
    // finish user task 2 and B with second user
    engineIntegrationExtension.finishAllRunningUserTasks(
      SECOND_USER,
      SECOND_USER,
      processInstanceDto.getId()
    );
  }

  private void finishUserTask1AWithDefaultAndLeaveTasks2BUnassigned(final ProcessInstanceEngineDto processInstanceDto) {
    // finish user task 1 and A with default user
    engineIntegrationExtension.finishAllRunningUserTasks(
      DEFAULT_USERNAME,
      DEFAULT_PASSWORD,
      processInstanceDto.getId()
    );
  }

  private HyperMapResultEntryDto createHyperMapResult(final String key,
                                                      final String label,
                                                      final MapResultEntryDto... results) {
    return new HyperMapResultEntryDto(key, List.of(results), label);
  }

  private ProcessReportDataDto createReport(final List<ReportDataDefinitionDto> definitionDtos) {
    final ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder.createReportData()
      .setReportDataType(USER_TASK_FREQ_GROUP_BY_ASSIGNEE_BY_PROCESS)
      .build();
    reportData.setDefinitions(definitionDtos);
    return reportData;
  }

  private String getLocalisedUnassignedLabel() {
    return embeddedOptimizeExtension.getLocalizationService()
      .getDefaultLocaleMessageForMissingAssigneeLabel();
  }

}
