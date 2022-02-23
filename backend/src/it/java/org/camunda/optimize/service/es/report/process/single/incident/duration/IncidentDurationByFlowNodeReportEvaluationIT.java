/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single.incident.duration;

import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.OpenIncidentFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ResolvedIncidentFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.service.es.report.util.MapResultAsserter;
import org.camunda.optimize.test.util.DateCreationFreezer;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.util.BpmnModels;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.AVERAGE;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.MAX;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.MEDIAN;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.MIN;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.PERCENTILE;
import static org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel.INSTANCE;
import static org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel.VIEW;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_KEY;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_VALUE;
import static org.camunda.optimize.service.es.report.process.single.incident.duration.IncidentDataDeployer.IncidentProcessType.ONE_TASK;
import static org.camunda.optimize.service.es.report.process.single.incident.duration.IncidentDataDeployer.IncidentProcessType.TWO_PARALLEL_TASKS;
import static org.camunda.optimize.service.es.report.process.single.incident.duration.IncidentDataDeployer.IncidentProcessType.TWO_SEQUENTIAL_TASKS;
import static org.camunda.optimize.service.es.report.process.single.incident.duration.IncidentDataDeployer.PROCESS_DEFINITION_KEY;
import static org.camunda.optimize.test.optimize.CollectionClient.DEFAULT_TENANT;
import static org.camunda.optimize.test.util.ProcessReportDataType.INCIDENT_DUR_GROUP_BY_FLOW_NODE;
import static org.camunda.optimize.util.BpmnModels.END_EVENT_ID_1;
import static org.camunda.optimize.util.BpmnModels.END_EVENT_ID_2;
import static org.camunda.optimize.util.BpmnModels.END_EVENT_NAME;
import static org.camunda.optimize.util.BpmnModels.END_EVENT_NAME_1;
import static org.camunda.optimize.util.BpmnModels.END_EVENT_NAME_2;
import static org.camunda.optimize.util.BpmnModels.SERVICE_TASK_ID_1;
import static org.camunda.optimize.util.BpmnModels.SERVICE_TASK_ID_2;
import static org.camunda.optimize.util.BpmnModels.SERVICE_TASK_NAME_1;
import static org.camunda.optimize.util.BpmnModels.SERVICE_TASK_NAME_2;
import static org.camunda.optimize.util.BpmnModels.SPLITTING_GATEWAY_ID;
import static org.camunda.optimize.util.BpmnModels.START_EVENT_ID;
import static org.camunda.optimize.util.BpmnModels.START_EVENT_NAME;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class IncidentDurationByFlowNodeReportEvaluationIT extends AbstractProcessDefinitionIT {

  private Stream<Consumer<Long>> startInstanceWithDifferentIncidentStates() {
    // @formatter:off
    return Stream.of(
      (durationInSec) -> IncidentDataDeployer.dataDeployer(incidentClient)
        .deployProcess(ONE_TASK)
        .startProcessInstance()
          .withOpenIncident()
          .withIncidentDurationInSec(durationInSec)
        .executeDeployment(),
      (durationInSec) -> IncidentDataDeployer.dataDeployer(incidentClient)
        .deployProcess(ONE_TASK)
        .startProcessInstance()
          .withResolvedIncident()
          .withIncidentDurationInSec(durationInSec)
        .executeDeployment(),
      (durationInSec) -> IncidentDataDeployer.dataDeployer(incidentClient)
        .deployProcess(ONE_TASK)
        .startProcessInstance()
          .withDeletedIncident()
          .withIncidentDurationInSec(durationInSec)
        .executeDeployment()
    );
    // @formatter:on
  }

  @ParameterizedTest
  @MethodSource("startInstanceWithDifferentIncidentStates")
  public void allIncidentStates(Consumer<Long> startProcessWithIncidentWithDurationInSec) {
    // given
    startProcessWithIncidentWithDurationInSec.accept(1L);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(PROCESS_DEFINITION_KEY, "1");
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(PROCESS_DEFINITION_KEY);
    assertThat(resultReportDataDto.getDefinitionVersions()).containsExactly("1");
    assertThat(resultReportDataDto.getView()).isNotNull();
    assertThat(resultReportDataDto.getView().getEntity()).isEqualTo(ProcessViewEntity.INCIDENT);
    assertThat(resultReportDataDto.getView().getFirstProperty()).isEqualTo(ViewProperty.DURATION);
    assertThat(resultReportDataDto.getGroupBy().getType()).isEqualTo(ProcessGroupByType.FLOW_NODES);

    final ReportResultResponseDto<List<MapResultEntryDto>> resultDto = evaluationResponse.getResult();
    // @formatter:off
    MapResultAsserter.asserter()
      .processInstanceCount(1L)
      .measure(ViewProperty.DURATION, new AggregationDto(AVERAGE))
        .groupedByContains(END_EVENT, null, END_EVENT_NAME)
        .groupedByContains(SERVICE_TASK_ID_1, 1000., SERVICE_TASK_NAME_1)
        .groupedByContains(START_EVENT, null, START_EVENT_NAME)
      .doAssert(resultDto);
    // @formatter:on
  }

  @Test
  public void customIncidentTypes() {
    // given
    // @formatter:off
    IncidentDataDeployer.dataDeployer(incidentClient)
      .deployProcess(ONE_TASK)
      .startProcessInstance()
        .withResolvedIncident()
        .withIncidentDurationInSec(1L)
      .startProcessInstance()
        .withOpenIncidentOfCustomType("myCustomIncidentType")
        .withIncidentDurationInSec(3L)
      .executeDeployment();
    // @formatter:on
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(IncidentDataDeployer.PROCESS_DEFINITION_KEY, "1");
    final ReportResultResponseDto<List<MapResultEntryDto>> resultDto = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    // @formatter:off
    MapResultAsserter.asserter()
      .processInstanceCount(2L)
      .measure(ViewProperty.DURATION, new AggregationDto(AVERAGE))
        .groupedByContains(END_EVENT, null, END_EVENT_NAME)
        .groupedByContains(SERVICE_TASK_ID_1, 2000., SERVICE_TASK_NAME_1)
        .groupedByContains(START_EVENT, null, START_EVENT_NAME)
      .doAssert(resultDto);
    // @formatter:on
  }

  @Test
  public void severalOpenIncidentsForMultipleProcessInstances() {
    // given
    // @formatter:off
    IncidentDataDeployer.dataDeployer(incidentClient)
      .deployProcess(ONE_TASK)
      .startProcessInstance()
        .withOpenIncident()
        .withIncidentDurationInSec(3L)
      .startProcessInstance()
        .withOpenIncident()
        .withIncidentDurationInSec(3L)
      .executeDeployment();
    // @formatter:on
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(PROCESS_DEFINITION_KEY, "1");
    final ReportResultResponseDto<List<MapResultEntryDto>> resultDto = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    // @formatter:off
    MapResultAsserter.asserter()
      .processInstanceCount(2L)
      .measure(ViewProperty.DURATION, new AggregationDto(AVERAGE))
        .groupedByContains(END_EVENT, null, END_EVENT_NAME)
        .groupedByContains(SERVICE_TASK_ID_1, 3000., SERVICE_TASK_NAME_1)
        .groupedByContains(START_EVENT, null, START_EVENT_NAME)
      .doAssert(resultDto);
    // @formatter:on
  }

  @Test
  public void severalResolvedIncidentsForMultipleProcessInstances() {
    // given
    // @formatter:off
    IncidentDataDeployer.dataDeployer(incidentClient)
      .deployProcess(ONE_TASK)
      .startProcessInstance()
        .withResolvedIncident()
        .withIncidentDurationInSec(3L)
      .startProcessInstance()
        .withResolvedIncident()
        .withIncidentDurationInSec(1L)
      .executeDeployment();
    // @formatter:on
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(PROCESS_DEFINITION_KEY, "1");
    final ReportResultResponseDto<List<MapResultEntryDto>> resultDto = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    // @formatter:off
    MapResultAsserter.asserter()
      .processInstanceCount(2L)
      .measure(ViewProperty.DURATION, new AggregationDto(AVERAGE))
        .groupedByContains(END_EVENT, null, END_EVENT_NAME)
        .groupedByContains(SERVICE_TASK_ID_1, 2000., SERVICE_TASK_NAME_1)
        .groupedByContains(START_EVENT, null, START_EVENT_NAME)
      .doAssert(resultDto);
    // @formatter:on
  }

  @Test
  public void differentIncidentStatesInTheSameReport() {
    // given
    // @formatter:off
    IncidentDataDeployer.dataDeployer(incidentClient)
      .deployProcess(ONE_TASK)
      .startProcessInstance()
        .withOpenIncident()
        .withIncidentDurationInSec(1L)
      .startProcessInstance()
        .withResolvedIncident()
        .withIncidentDurationInSec(2L)
      .startProcessInstance()
        .withDeletedIncident()
        .withIncidentDurationInSec(6L)
      .executeDeployment();
    // @formatter:on
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(PROCESS_DEFINITION_KEY, "1");
    final ReportResultResponseDto<List<MapResultEntryDto>> resultDto = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    // @formatter:off
    MapResultAsserter.asserter()
      .processInstanceCount(3L)
      .measure(ViewProperty.DURATION, new AggregationDto(AVERAGE))
        .groupedByContains(END_EVENT, null, END_EVENT_NAME)
        .groupedByContains(SERVICE_TASK_ID_1, 3000., SERVICE_TASK_NAME_1) // uses the average by default
        .groupedByContains(START_EVENT, null, START_EVENT_NAME)
      .doAssert(resultDto);
    // @formatter:on
  }

  @Test
  public void severalOpenIncidentsForMultipleProcessDefinitions() {
    // given
    final String key1 = "key1";
    final String key2 = "key2";
    // @formatter:off
    IncidentDataDeployer.dataDeployer(incidentClient)
      .key(key1)
      .deployProcess(ONE_TASK)
      .startProcessInstance()
        .withResolvedIncident()
        .withIncidentDurationInSec(50L)
      .executeDeployment();

    IncidentDataDeployer.dataDeployer(incidentClient)
      .key(key2)
      .deployProcess(TWO_PARALLEL_TASKS)
      .startProcessInstance()
        .withOpenIncident()
        .withIncidentDurationInSec(30L)
      .executeDeployment();
    // @formatter:on
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(key1, ALL_VERSIONS);
    reportData.getDefinitions().add(createReportDataDefinitionDto(key2));
    final ReportResultResponseDto<List<MapResultEntryDto>> resultDto =
      reportClient.evaluateMapReport(reportData).getResult();

    // then
    // @formatter:off
    MapResultAsserter.asserter()
      .processInstanceCount(2L)
      .measure(ViewProperty.DURATION, new AggregationDto(AVERAGE))
        .groupedByContains(END_EVENT, null, END_EVENT_NAME)
        .groupedByContains(END_EVENT_ID_1, null, END_EVENT_NAME_1)
        .groupedByContains(END_EVENT_ID_2, null, END_EVENT_NAME_2)
        // 50 + 30 / 2
        .groupedByContains(SERVICE_TASK_ID_1, 40_000., SERVICE_TASK_NAME_1)
        .groupedByContains(SERVICE_TASK_ID_2, 30_000., SERVICE_TASK_NAME_2)
        .groupedByContains(SPLITTING_GATEWAY_ID, null, SPLITTING_GATEWAY_ID)
        .groupedByContains(START_EVENT, null, START_EVENT_NAME)
      .doAssert(resultDto);
    // @formatter:on
  }

  @Test
  public void otherProcessDefinitionVersionsDoNoAffectResult() {
    // given
    // @formatter:off
    IncidentDataDeployer.dataDeployer(incidentClient)
      .deployProcess(ONE_TASK)
      .startProcessInstance()
      .withResolvedIncident()
      .withIncidentDurationInSec(55L)
      .executeDeployment();

    IncidentDataDeployer.dataDeployer(incidentClient)
      .deployProcess(ONE_TASK)
      .startProcessInstance()
      .withResolvedIncident()
      .withIncidentDurationInSec(22L)
      .executeDeployment();
    // @formatter:on
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(PROCESS_DEFINITION_KEY, "1");
    final ReportResultResponseDto<List<MapResultEntryDto>> resultDto = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    // @formatter:off
    MapResultAsserter.asserter()
      .processInstanceCount(1L)
      .measure(ViewProperty.DURATION, new AggregationDto(AVERAGE))
        .groupedByContains(END_EVENT, null, END_EVENT_NAME)
        .groupedByContains(SERVICE_TASK_ID_1, 55_000., SERVICE_TASK_NAME_1)
        .groupedByContains(START_EVENT, null, START_EVENT_NAME)
      .doAssert(resultDto);
    // @formatter:on
  }

  @Test
  public void incidentReportAcrossMultipleDefinitionVersions() {
    // given
    // @formatter:off
    IncidentDataDeployer.dataDeployer(incidentClient)
      .deployProcess(ONE_TASK)
      .startProcessInstance()
        .withResolvedIncident()
        .withIncidentDurationInSec(1L)
      .executeDeployment();

    IncidentDataDeployer.dataDeployer(incidentClient)
      .deployProcess(ONE_TASK)
      .startProcessInstance()
        .withResolvedIncident()
        .withIncidentDurationInSec(5L)
      .executeDeployment();
    // @formatter:on
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(PROCESS_DEFINITION_KEY, ReportConstants.ALL_VERSIONS);
    final ReportResultResponseDto<List<MapResultEntryDto>> resultDto = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    // @formatter:off
    MapResultAsserter.asserter()
      .processInstanceCount(2L)
      .measure(ViewProperty.DURATION, new AggregationDto(AVERAGE))
        .groupedByContains(END_EVENT, null, END_EVENT_NAME)
        .groupedByContains(SERVICE_TASK_ID_1, 3000., SERVICE_TASK_NAME_1)
        .groupedByContains(START_EVENT, null, START_EVENT_NAME)
      .doAssert(resultDto);
    // @formatter:on
  }

  @Test
  public void allVersionsRespectLatestNodesOnlyWhereLatestHasMoreNodes() {
    // given
    // @formatter:off
    IncidentDataDeployer.dataDeployer(incidentClient)
      .deployProcess(ONE_TASK)
      .startProcessInstance()
        .withResolvedIncident()
        .withIncidentDurationInSec(1L)
      .executeDeployment();
    IncidentDataDeployer.dataDeployer(incidentClient)
      .deployProcess(TWO_SEQUENTIAL_TASKS)
      .startProcessInstance()
        .withResolvedAndOpenIncident()
        .withIncidentDurationInSec(3L)
      .executeDeployment();
    // @formatter:on
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(PROCESS_DEFINITION_KEY, ALL_VERSIONS);
    ReportResultResponseDto<List<MapResultEntryDto>> resultDto = reportClient.evaluateMapReport(reportData).getResult();

    // then
    // @formatter:off
    MapResultAsserter.asserter()
      .processInstanceCount(2L)
      .measure(ViewProperty.DURATION, new AggregationDto(AVERAGE))
        .groupedByContains(END_EVENT, null, END_EVENT_NAME)
        .groupedByContains(SERVICE_TASK_ID_1, 2000., SERVICE_TASK_NAME_1)
        .groupedByContains(SERVICE_TASK_ID_2, 3000., SERVICE_TASK_NAME_2)
        .groupedByContains(START_EVENT, null, START_EVENT_NAME)
      .doAssert(resultDto);
    // @formatter:on
  }

  @Test
  public void multipleVersionsRespectLatestNodesOnlyWhereLatestHasMoreNodes() {
    // given
    // @formatter:off
    IncidentDataDeployer.dataDeployer(incidentClient)
      .deployProcess(ONE_TASK)
      .startProcessInstance()
        .withResolvedIncident()
        .withIncidentDurationInSec(1L)
      .executeDeployment();
    IncidentDataDeployer.dataDeployer(incidentClient)
      .deployProcess(TWO_SEQUENTIAL_TASKS)
      .startProcessInstance()
        .withResolvedAndOpenIncident()
        .withIncidentDurationInSec(3L)
      .executeDeployment();
    // @formatter:on
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(PROCESS_DEFINITION_KEY, "1", "2");
    ReportResultResponseDto<List<MapResultEntryDto>> resultDto = reportClient.evaluateMapReport(reportData).getResult();

    // then
    // @formatter:off
    MapResultAsserter.asserter()
      .processInstanceCount(2L)
      .measure(ViewProperty.DURATION, new AggregationDto(AVERAGE))
        .groupedByContains(END_EVENT, null, END_EVENT_NAME)
        .groupedByContains(SERVICE_TASK_ID_1, 2000., SERVICE_TASK_NAME_1)
        .groupedByContains(SERVICE_TASK_ID_2, 3000., SERVICE_TASK_NAME_2)
        .groupedByContains(START_EVENT, null, START_EVENT_NAME)
      .doAssert(resultDto);
    // @formatter:on
  }

  @Test
  public void allVersionsRespectLatestNodesOnlyWhereLatestHasFewerNodes() {
    // given
    // @formatter:off
    IncidentDataDeployer.dataDeployer(incidentClient)
      .deployProcess(TWO_SEQUENTIAL_TASKS)
      .startProcessInstance()
        .withResolvedAndOpenIncident()
        .withIncidentDurationInSec(3L)
      .executeDeployment();
    IncidentDataDeployer.dataDeployer(incidentClient)
      .deployProcess(ONE_TASK)
      .startProcessInstance()
        .withResolvedIncident()
        .withIncidentDurationInSec(1L)
      .executeDeployment();
    // @formatter:on
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(PROCESS_DEFINITION_KEY, ALL_VERSIONS);
    ReportResultResponseDto<List<MapResultEntryDto>> resultDto = reportClient.evaluateMapReport(reportData).getResult();

    // then
    MapResultAsserter.asserter()
      .processInstanceCount(2L)
      .measure(ViewProperty.DURATION, new AggregationDto(AVERAGE))
      .groupedByContains(END_EVENT, null, END_EVENT_NAME)
      .groupedByContains(SERVICE_TASK_ID_1, 2000., SERVICE_TASK_NAME_1)
      .groupedByContains(START_EVENT, null, START_EVENT_NAME)
      .doAssert(resultDto);
  }

  @Test
  public void multipleVersionsRespectLatestNodesOnlyWhereLatestHasFewerNodes() {
    // given
    // @formatter:off
    IncidentDataDeployer.dataDeployer(incidentClient)
      .deployProcess(TWO_SEQUENTIAL_TASKS)
      .startProcessInstance()
        .withResolvedAndOpenIncident()
        .withIncidentDurationInSec(3L)
      .executeDeployment();
    IncidentDataDeployer.dataDeployer(incidentClient)
      .deployProcess(ONE_TASK)
      .startProcessInstance()
        .withResolvedIncident()
        .withIncidentDurationInSec(1L)
      .executeDeployment();
    // @formatter:on
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(PROCESS_DEFINITION_KEY, "1", "2");
    ReportResultResponseDto<List<MapResultEntryDto>> resultDto = reportClient.evaluateMapReport(reportData).getResult();

    // then
    MapResultAsserter.asserter()
      .processInstanceCount(2L)
      .measure(ViewProperty.DURATION, new AggregationDto(AVERAGE))
      .groupedByContains(END_EVENT, null, END_EVENT_NAME)
      .groupedByContains(SERVICE_TASK_ID_1, 2000., SERVICE_TASK_NAME_1)
      .groupedByContains(START_EVENT, null, START_EVENT_NAME)
      .doAssert(resultDto);
  }

  @Test
  public void reportEvaluationFilteredBySingleTenant() {
    // given
    final String tenantId1 = "tenantId1";
    final String tenantId2 = "tenantId2";
    final List<String> selectedTenants = Collections.singletonList(tenantId1);
    engineIntegrationExtension.createTenant(tenantId1);
    engineIntegrationExtension.createTenant(tenantId2);

    OffsetDateTime creationDate = DateCreationFreezer.dateFreezer().freezeDateAndReturn();
    final ProcessInstanceEngineDto processInstanceEngineDto1 =
      incidentClient.deployAndStartProcessInstanceWithTenantAndWithOpenIncident(tenantId1);
    engineDatabaseExtension.changeIncidentCreationDate(
      processInstanceEngineDto1.getId(),
      creationDate.minusSeconds(1L)
    );
    final ProcessInstanceEngineDto processInstanceEngineDto2 =
      incidentClient.deployAndStartProcessInstanceWithTenantAndWithOpenIncident(tenantId2);
    engineDatabaseExtension.changeIncidentCreationDate(
      processInstanceEngineDto2.getId(),
      creationDate.minusSeconds(2L)
    );
    final ProcessInstanceEngineDto processInstanceEngineDto3 =
      incidentClient.deployAndStartProcessInstanceWithTenantAndWithOpenIncident(DEFAULT_TENANT);
    engineDatabaseExtension.changeIncidentCreationDate(
      processInstanceEngineDto3.getId(),
      creationDate.minusSeconds(3L)
    );

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(
      processInstanceEngineDto1.getProcessDefinitionKey(),
      ReportConstants.ALL_VERSIONS
    );
    reportData.setTenantIds(selectedTenants);
    ReportResultResponseDto<List<MapResultEntryDto>> resultDto = reportClient.evaluateMapReport(reportData).getResult();

    // then
    MapResultAsserter.asserter()
      .processInstanceCount(1L)
      .measure(ViewProperty.DURATION, new AggregationDto(AVERAGE))
      .groupedByContains(END_EVENT, null, END_EVENT_NAME)
      .groupedByContains(SERVICE_TASK_ID_1, 1000., SERVICE_TASK_NAME_1)
      .groupedByContains(START_EVENT, null, START_EVENT_NAME)
      .doAssert(resultDto);
  }

  @Test
  public void filterInReport() {
    // given two process instances
    // Instance 1: one incident in task 1 (resolved) which completes the process instance
    // Instance 2: one incident in task 1 (open) and because of that the task is still pending.
    // Hint: failExternalTasks method does not complete the tasks
    // @formatter:off
    IncidentDataDeployer.dataDeployer(incidentClient)
      .deployProcess(ONE_TASK)
      .startProcessInstance()
        .withResolvedIncident()
        .withIncidentDurationInSec(1L)
      .startProcessInstance()
        .withOpenIncident()
        .withIncidentDurationInSec(3L)
      .executeDeployment();
    // @formatter:on
    importAllEngineEntitiesFromScratch();

    // when I create a report without filters
    ProcessReportDataDto reportData = createReport(PROCESS_DEFINITION_KEY, ReportConstants.ALL_VERSIONS);
    ReportResultResponseDto<List<MapResultEntryDto>> resultDto = reportClient.evaluateMapReport(reportData).getResult();

    // then the result has two process instances
    MapResultAsserter.asserter()
      .processInstanceCount(2L)
      .measure(ViewProperty.DURATION, new AggregationDto(AVERAGE))
      .groupedByContains(END_EVENT, null, END_EVENT_NAME)
      .groupedByContains(SERVICE_TASK_ID_1, 2000., SERVICE_TASK_NAME_1)
      .groupedByContains(START_EVENT, null, START_EVENT_NAME)
      .doAssert(resultDto);

    // when I create a running process instances only filter
    List<ProcessFilterDto<?>> runningProcessInstancesOnly = ProcessFilterBuilder
      .filter()
      .runningInstancesOnly()
      .add()
      .buildList();
    reportData.setFilter(runningProcessInstancesOnly);
    resultDto = reportClient.evaluateMapReport(reportData).getResult();

    // then we only get instance 1 because there's only one running
    MapResultAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.DURATION, new AggregationDto(AVERAGE))
      .groupedByContains(END_EVENT, null, END_EVENT_NAME)
      .groupedByContains(SERVICE_TASK_ID_1, 3000., SERVICE_TASK_NAME_1)
      .groupedByContains(START_EVENT, null, START_EVENT_NAME)
      .doAssert(resultDto);
  }

  private Stream<Arguments> instanceLevelIncidentFilter() {
    return Stream.of(
      Arguments.of(
        ProcessFilterBuilder.filter().withOpenIncident().filterLevel(INSTANCE).add().buildList(), 3000.),
      Arguments.of(ProcessFilterBuilder.filter().withResolvedIncident().filterLevel(INSTANCE).add().buildList(), 1000.),
      Arguments.of(ProcessFilterBuilder.filter().noIncidents().filterLevel(INSTANCE).add().buildList(), null)
    );
  }

  @ParameterizedTest
  @MethodSource("instanceLevelIncidentFilter")
  public void instanceLevelIncidentFilterIsAppliedAtInstanceLevel(final List<ProcessFilterDto<?>> filter,
                                                                  final Double expectedIncidentCount) {
    // @formatter:off
    IncidentDataDeployer.dataDeployer(incidentClient)
      .deployProcess(ONE_TASK)
      .startProcessInstance()
        .withResolvedIncident()
        .withIncidentDurationInSec(1L)
      .startProcessInstance()
        .withOpenIncident()
        .withIncidentDurationInSec(3L)
      .startProcessInstance()
        .withoutIncident()
      .executeDeployment();
    // @formatter:on
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(PROCESS_DEFINITION_KEY, ReportConstants.ALL_VERSIONS);
    reportData.setFilter(filter);
    ReportResultResponseDto<List<MapResultEntryDto>> resultDto = reportClient.evaluateMapReport(reportData).getResult();

    // then
    MapResultAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(3L)
      .measure(ViewProperty.DURATION, new AggregationDto(AVERAGE))
      .groupedByContains(END_EVENT, null, END_EVENT_NAME)
      .groupedByContains(SERVICE_TASK_ID_1, expectedIncidentCount, SERVICE_TASK_NAME_1)
      .groupedByContains(START_EVENT, null, START_EVENT_NAME)
      .doAssert(resultDto);
  }

  private Stream<Arguments> viewLevelIncidentFilter() {
    return Stream.of(
      Arguments.of(
        ProcessFilterBuilder.filter().withOpenIncident().filterLevel(VIEW).add().buildList(), 1, null, 2000.),
      Arguments.of(
        ProcessFilterBuilder.filter().withResolvedIncident().filterLevel(VIEW).add().buildList(), 2, 1500., null)
    );
  }

  @ParameterizedTest
  @MethodSource("viewLevelIncidentFilter")
  public void viewLevelIncidentFilterIsAppliedAtViewLevel(final List<ProcessFilterDto<?>> filter,
                                                          final Integer expectedInstanceCount,
                                                          final Double firstExpectedResult,
                                                          final Double secondExpectedResult) {
    // @formatter:off
    IncidentDataDeployer.dataDeployer(incidentClient)
      .deployProcess(TWO_SEQUENTIAL_TASKS)
      .startProcessInstance()
        .withResolvedIncident()
        .withIncidentDurationInSec(1L)
      .startProcessInstance()
        .withResolvedAndOpenIncident()
        .withIncidentDurationInSec(2L)
      .executeDeployment();
    // @formatter:on
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(PROCESS_DEFINITION_KEY, ReportConstants.ALL_VERSIONS);
    reportData.setFilter(filter);
    ReportResultResponseDto<List<MapResultEntryDto>> resultDto = reportClient.evaluateMapReport(reportData).getResult();

    // then
    final MapResultAsserter asserter = MapResultAsserter.asserter()
      .processInstanceCount(expectedInstanceCount)
      .processInstanceCountWithoutFilters(2L);
    Optional.ofNullable(firstExpectedResult)
      .ifPresent(result -> asserter.measure(ViewProperty.DURATION, new AggregationDto(AVERAGE))
        .groupedByContains(SERVICE_TASK_ID_1, result, SERVICE_TASK_NAME_1)
        .add());
    Optional.ofNullable(secondExpectedResult)
      .ifPresent(result -> asserter.measure(ViewProperty.DURATION, new AggregationDto(AVERAGE))
        .groupedByContains(SERVICE_TASK_ID_2, result, SERVICE_TASK_NAME_2)
        .add());
    asserter.doAssert(resultDto);
  }

  private static Stream<List<ProcessFilterDto<?>>> nonIncidentViewLevelFilters() {
    return viewLevelFilters()
      .filter(filters -> {
        final ProcessFilterDto<?> filter = filters.get(0);
        return !(filter instanceof OpenIncidentFilterDto) && !(filter instanceof ResolvedIncidentFilterDto);
      });
  }

  @ParameterizedTest
  @MethodSource("nonIncidentViewLevelFilters")
  public void otherViewTypeViewLevelFiltersOnlyAppliedToInstances(final List<ProcessFilterDto<?>> filtersToApply) {
    IncidentDataDeployer.dataDeployer(incidentClient)
      .deployProcess(ONE_TASK)
      .startProcessInstance()
      .withResolvedIncident()
      .withIncidentDurationInSec(1L)
      .startProcessInstance()
      .withOpenIncident()
      .withIncidentDurationInSec(3L)
      .executeDeployment();
    // @formatter:on
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(PROCESS_DEFINITION_KEY, ReportConstants.ALL_VERSIONS);
    reportData.setFilter(filtersToApply);
    ReportResultResponseDto<List<MapResultEntryDto>> resultDto = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(resultDto.getInstanceCount()).isZero();
    assertThat(resultDto.getInstanceCountWithoutFilters()).isEqualTo(2L);
    assertThat(resultDto.getFirstMeasureData()).isEmpty();
  }

  @Test
  public void noIncidentReturnsNullAsResult() {
    // given
    final ProcessInstanceEngineDto processInstanceEngineDto =
      engineIntegrationExtension.deployAndStartProcess(BpmnModels.getSimpleBpmnDiagram());
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(
      processInstanceEngineDto.getProcessDefinitionKey(),
      processInstanceEngineDto.getProcessDefinitionVersion()
    );
    final ReportResultResponseDto<List<MapResultEntryDto>> resultDto = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    // @formatter:off
    MapResultAsserter.asserter()
      .processInstanceCount(1L)
      .measure(ViewProperty.DURATION, new AggregationDto(AVERAGE))
      .groupedByContains(END_EVENT, null)
      .groupedByContains(START_EVENT, null)
      .doAssert(resultDto);
    // @formatter:on
  }

  @Test
  public void processInstanceWithIncidentAndOneWithoutIncident() {
    // given
    // @formatter:off
    IncidentDataDeployer.dataDeployer(incidentClient)
      .deployProcess(ONE_TASK)
      .startProcessInstance()
        .withoutIncident()
      .startProcessInstance()
        .withOpenIncident()
        .withIncidentDurationInSec(3L)
      .executeDeployment();
    // @formatter:on
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(PROCESS_DEFINITION_KEY, "1");
    final ReportResultResponseDto<List<MapResultEntryDto>> resultDto = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    // @formatter:off
    MapResultAsserter.asserter()
      .processInstanceCount(2L)
      .measure(ViewProperty.DURATION, new AggregationDto(AVERAGE))
        .groupedByContains(END_EVENT, null, END_EVENT_NAME)
        .groupedByContains(SERVICE_TASK_ID_1, 3000., SERVICE_TASK_NAME_1)
        .groupedByContains(START_EVENT, null, START_EVENT_NAME)
      .doAssert(resultDto);
    // @formatter:on
  }

  private Stream<Arguments> aggregationTypes() {
    // @formatter:off
    return Stream.of(
      Arguments.of(new AggregationDto(MIN), 1000.),
      Arguments.of(new AggregationDto(MAX), 9000.),
      Arguments.of(new AggregationDto(AVERAGE), 4000.),
      Arguments.of(new AggregationDto(MEDIAN), 2000.),
      Arguments.of(new AggregationDto(PERCENTILE, 99.), 9000.),
      Arguments.of(new AggregationDto(PERCENTILE, 95.), 9000.),
      Arguments.of(new AggregationDto(PERCENTILE, 75.), 7250.),
      Arguments.of(new AggregationDto(PERCENTILE, 25.), 1250.),
      Arguments.of(new AggregationDto(PERCENTILE, 100.), 9000.),
      Arguments.of(new AggregationDto(PERCENTILE, 0.), 1000.)
    );
    // @formatter:on
  }

  @ParameterizedTest
  @MethodSource("aggregationTypes")
  public void aggregationTypes(final AggregationDto aggregationType, final double expectedResult) {
    // given
    // @formatter:off
    IncidentDataDeployer.dataDeployer(incidentClient)
      .deployProcess(ONE_TASK)
      .startProcessInstance()
        .withResolvedIncident()
        .withIncidentDurationInSec(1L)
      .startProcessInstance()
        .withResolvedIncident()
        .withIncidentDurationInSec(2L)
      .startProcessInstance()
        .withResolvedIncident()
        .withIncidentDurationInSec(9L)
      .executeDeployment();
    // @formatter:on
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(PROCESS_DEFINITION_KEY, "1");
    reportData.getConfiguration().setAggregationTypes(aggregationType);
    final ReportResultResponseDto<List<MapResultEntryDto>> resultDto =
      reportClient.evaluateMapReport(reportData).getResult();

    // then
    // @formatter:off
    MapResultAsserter.asserter()
      .processInstanceCount(3L)
      .measure(ViewProperty.DURATION, aggregationType)
        .groupedByContains(END_EVENT, null, END_EVENT_NAME)
        .groupedByContains(SERVICE_TASK_ID_1, expectedResult, SERVICE_TASK_NAME_1)
        .groupedByContains(START_EVENT, null, START_EVENT_NAME)
      .doAssert(resultDto);
    // @formatter:on
  }

  @Test
  public void testCustomOrderOnResultKeyIsApplied() {
    // given
    // @formatter:off
    IncidentDataDeployer.dataDeployer(incidentClient)
      .deployProcess(TWO_PARALLEL_TASKS)
      .startProcessInstance()
        .withOpenIncident()
        .withIncidentDurationInSec(2L)
      .startProcessInstance()
        .withOpenIncident()
        .withIncidentDurationInSec(2L)
      .executeDeployment();
    // @formatter:on
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(PROCESS_DEFINITION_KEY, "1");
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_KEY, SortOrder.DESC));
    final ReportResultResponseDto<List<MapResultEntryDto>> resultDto = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    // @formatter:off
    MapResultAsserter.asserter()
      .processInstanceCount(2L)
      .measure(ViewProperty.DURATION, new AggregationDto(AVERAGE))
        .groupedByContains(START_EVENT_ID, null, START_EVENT_NAME)
        .groupedByContains(SPLITTING_GATEWAY_ID, null)
        .groupedByContains(SERVICE_TASK_ID_2, 2000., SERVICE_TASK_NAME_2)
        .groupedByContains(SERVICE_TASK_ID_1, 2000., SERVICE_TASK_NAME_1)
        .groupedByContains(END_EVENT_ID_2, null, END_EVENT_NAME_2)
        .groupedByContains(END_EVENT_ID_1, null, END_EVENT_NAME_1)
      .doAssert(resultDto);
    // @formatter:on
  }

  @Test
  public void testCustomOrderOnResultValueIsApplied() {
    // given
    // @formatter:off
    IncidentDataDeployer.dataDeployer(incidentClient)
      .deployProcess(TWO_SEQUENTIAL_TASKS)
      .startProcessInstance()
        .withResolvedAndOpenIncident()
        .withIncidentDurationInSec(3L)
      .startProcessInstance()
        .withResolvedIncident()
        .withIncidentDurationInSec(1L)
      .executeDeployment();
    // @formatter:on
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(PROCESS_DEFINITION_KEY, "1");
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_VALUE, SortOrder.DESC));
    final ReportResultResponseDto<List<MapResultEntryDto>> resultDto = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    // @formatter:off
    MapResultAsserter.asserter()
      .processInstanceCount(2L)
      .measure(ViewProperty.DURATION, new AggregationDto(AVERAGE))
        .groupedByContains(SERVICE_TASK_ID_2, 3000., SERVICE_TASK_NAME_2)
        .groupedByContains(SERVICE_TASK_ID_1, 2000., SERVICE_TASK_NAME_1)
        .groupedByContains(END_EVENT, null, END_EVENT_NAME)
        .groupedByContains(START_EVENT, null, START_EVENT_NAME)
      .doAssert(resultDto);
    // @formatter:on
  }

  private ProcessReportDataDto createReport(String processDefinitionKey, String... processDefinitionVersions) {
    return TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersions(Arrays.asList(processDefinitionVersions))
      .setReportDataType(INCIDENT_DUR_GROUP_BY_FLOW_NODE)
      .build();
  }

}
