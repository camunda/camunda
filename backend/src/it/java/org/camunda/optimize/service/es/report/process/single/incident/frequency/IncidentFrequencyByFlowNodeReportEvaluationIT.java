/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.process.single.incident.frequency;

import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
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
import org.camunda.optimize.service.es.report.process.single.incident.duration.IncidentDataDeployer;
import org.camunda.optimize.service.es.report.util.MapResultAsserter;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel.INSTANCE;
import static org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel.VIEW;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_KEY;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_VALUE;
import static org.camunda.optimize.service.es.report.process.single.incident.duration.IncidentDataDeployer.IncidentProcessType.ONE_TASK;
import static org.camunda.optimize.service.es.report.process.single.incident.duration.IncidentDataDeployer.IncidentProcessType.TWO_PARALLEL_TASKS;
import static org.camunda.optimize.service.es.report.process.single.incident.duration.IncidentDataDeployer.IncidentProcessType.TWO_SEQUENTIAL_TASKS;
import static org.camunda.optimize.service.es.report.process.single.incident.duration.IncidentDataDeployer.PROCESS_DEFINITION_KEY;
import static org.camunda.optimize.test.optimize.CollectionClient.DEFAULT_TENANT;
import static org.camunda.optimize.test.util.ProcessReportDataType.INCIDENT_FREQ_GROUP_BY_FLOW_NODE;
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
public class IncidentFrequencyByFlowNodeReportEvaluationIT extends AbstractProcessDefinitionIT {

  private Stream<Runnable> startInstanceWithDifferentIncidents() {
    return Stream.of(
      () -> IncidentDataDeployer.dataDeployer(incidentClient)
        .deployProcess(TWO_PARALLEL_TASKS)
        .startProcessInstance()
        .withOpenIncident()
        .executeDeployment(),
      () -> IncidentDataDeployer.dataDeployer(incidentClient)
        .deployProcess(TWO_PARALLEL_TASKS)
        .startProcessInstance()
        .withResolvedIncident()
        .executeDeployment(),
      () -> IncidentDataDeployer.dataDeployer(incidentClient)
        .deployProcess(TWO_PARALLEL_TASKS)
        .startProcessInstance()
        .withDeletedIncident()
        .executeDeployment()
    );
  }

  @ParameterizedTest
  @MethodSource("startInstanceWithDifferentIncidents")
  public void twoIncidentsInOneProcessInstance(Runnable startAndReturnProcessInstanceWithTwoIncidents) {
    // given
    startAndReturnProcessInstanceWithTwoIncidents.run();
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
    assertThat(resultReportDataDto.getView().getFirstProperty()).isEqualTo(ViewProperty.FREQUENCY);
    assertThat(resultReportDataDto.getGroupBy().getType()).isEqualTo(ProcessGroupByType.FLOW_NODES);

    final ReportResultResponseDto<List<MapResultEntryDto>> resultDto = evaluationResponse.getResult();
    // @formatter:off
    MapResultAsserter.asserter()
      .processInstanceCount(1L)
      .measure(ViewProperty.FREQUENCY)
        .groupedByContains(END_EVENT_ID_1, null, END_EVENT_NAME_1)
        .groupedByContains(END_EVENT_ID_2, null, END_EVENT_NAME_2)
        .groupedByContains(SERVICE_TASK_ID_1, 1., SERVICE_TASK_NAME_1)
        .groupedByContains(SERVICE_TASK_ID_2, 1., SERVICE_TASK_NAME_2)
        .groupedByContains(SPLITTING_GATEWAY_ID, null)
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
        .withOpenIncident()
      .startProcessInstance()
        .withOpenIncidentOfCustomType("myCustomIncidentType")
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
      .measure(ViewProperty.FREQUENCY)
        .groupedByContains(END_EVENT, null, END_EVENT_NAME)
        .groupedByContains(SERVICE_TASK_ID_1, 2., SERVICE_TASK_NAME_1)
        .groupedByContains(START_EVENT, null, START_EVENT_NAME)
      .doAssert(resultDto);
    // @formatter:on
  }

  @Test
  public void incidentsForMultipleProcessInstances() {
    // given
    // @formatter:off
    IncidentDataDeployer.dataDeployer(incidentClient)
      .deployProcess(ONE_TASK)
      .startProcessInstance()
        .withOpenIncident()
      .startProcessInstance()
        .withOpenIncident()
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
      .measure(ViewProperty.FREQUENCY)
        .groupedByContains(END_EVENT, null, END_EVENT_NAME)
        .groupedByContains(SERVICE_TASK_ID_1, 2., SERVICE_TASK_NAME_1)
        .groupedByContains(START_EVENT, null, START_EVENT_NAME)
      .doAssert(resultDto);
    // @formatter:on
  }

  @Test
  public void incidentsForMultipleProcessDefinitions() {
    // given
    final String key1 = "key1";
    final String key2 = "key2";
    // @formatter:off
    IncidentDataDeployer.dataDeployer(incidentClient)
      .key(key1)
      .deployProcess(ONE_TASK)
      .startProcessInstance()
      .withResolvedIncident()
      .executeDeployment();

    IncidentDataDeployer.dataDeployer(incidentClient)
      .key(key2)
      .deployProcess(TWO_PARALLEL_TASKS)
      .startProcessInstance()
      .withOpenIncident()
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
      .measure(ViewProperty.FREQUENCY)
        .groupedByContains(END_EVENT, null, END_EVENT_NAME)
        .groupedByContains(END_EVENT_ID_1, null, END_EVENT_NAME_1)
        .groupedByContains(END_EVENT_ID_2, null, END_EVENT_NAME_2)
        .groupedByContains(SERVICE_TASK_ID_1, 2., SERVICE_TASK_NAME_1)
        .groupedByContains(SERVICE_TASK_ID_2, 1., SERVICE_TASK_NAME_2)
        .groupedByContains(SPLITTING_GATEWAY_ID, null, SPLITTING_GATEWAY_ID)
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
      .startProcessInstance()
        .withResolvedIncident()
      .startProcessInstance()
        .withDeletedIncident()
      .startProcessInstance()
        .withoutIncident()
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
      .processInstanceCount(4L)
      .measure(ViewProperty.FREQUENCY)
        .groupedByContains(END_EVENT, null, END_EVENT_NAME)
        .groupedByContains(SERVICE_TASK_ID_1, 3., SERVICE_TASK_NAME_1)
        .groupedByContains(START_EVENT, null, START_EVENT_NAME)
      .doAssert(resultDto);
    // @formatter:on
  }

  @Test
  public void otherProcessDefinitionVersionsDoNoAffectResult() {
    // given
    final ProcessInstanceEngineDto processInstance = incidentClient.deployAndStartProcessInstanceWithOpenIncident();
    incidentClient.deployAndStartProcessInstanceWithOpenIncident();
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(
      processInstance.getProcessDefinitionKey(),
      processInstance.getProcessDefinitionVersion()
    );
    final ReportResultResponseDto<List<MapResultEntryDto>> resultDto = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    // @formatter:off
    MapResultAsserter.asserter()
      .processInstanceCount(1L)
      .measure(ViewProperty.FREQUENCY)
        .groupedByContains(END_EVENT, null, END_EVENT_NAME)
        .groupedByContains(SERVICE_TASK_ID_1, 1., SERVICE_TASK_NAME_1)
        .groupedByContains(START_EVENT, null, START_EVENT_NAME)
      .doAssert(resultDto);
    // @formatter:on
  }

  @Test
  public void incidentReportAcrossMultipleDefinitionVersions() {
    // given
    final ProcessInstanceEngineDto processInstance = incidentClient.deployAndStartProcessInstanceWithOpenIncident();
    incidentClient.deployAndStartProcessInstanceWithOpenIncident();
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(
      processInstance.getProcessDefinitionKey(),
      ReportConstants.ALL_VERSIONS
    );
    final ReportResultResponseDto<List<MapResultEntryDto>> resultDto =
      reportClient.evaluateMapReport(reportData).getResult();

    // then
    // @formatter:off
    MapResultAsserter.asserter()
      .processInstanceCount(2L)
      .measure(ViewProperty.FREQUENCY)
        .groupedByContains(END_EVENT, null, END_EVENT_NAME)
        .groupedByContains(SERVICE_TASK_ID_1, 2., SERVICE_TASK_NAME_1)
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
      .withOpenIncident()
      .executeDeployment();
    IncidentDataDeployer.dataDeployer(incidentClient)
      .deployProcess(TWO_SEQUENTIAL_TASKS)
      .startProcessInstance()
      .withOpenIncident()
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
      .measure(ViewProperty.FREQUENCY)
        .groupedByContains(END_EVENT, null, END_EVENT_NAME)
        .groupedByContains(SERVICE_TASK_ID_1, 2., SERVICE_TASK_NAME_1)
        .groupedByContains(SERVICE_TASK_ID_2, null, SERVICE_TASK_NAME_2)
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
      .withOpenIncident()
      .executeDeployment();
    IncidentDataDeployer.dataDeployer(incidentClient)
      .deployProcess(TWO_SEQUENTIAL_TASKS)
      .startProcessInstance()
      .withOpenIncident()
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
      .measure(ViewProperty.FREQUENCY)
        .groupedByContains(END_EVENT, null, END_EVENT_NAME)
        .groupedByContains(SERVICE_TASK_ID_1, 2., SERVICE_TASK_NAME_1)
        .groupedByContains(SERVICE_TASK_ID_2, null, SERVICE_TASK_NAME_2)
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
      .withOpenIncident()
      .executeDeployment();
    IncidentDataDeployer.dataDeployer(incidentClient)
      .deployProcess(ONE_TASK)
      .startProcessInstance()
      .withOpenIncident()
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
      .measure(ViewProperty.FREQUENCY)
        .groupedByContains(END_EVENT, null, END_EVENT_NAME)
        .groupedByContains(SERVICE_TASK_ID_1, 2., SERVICE_TASK_NAME_1)
        .groupedByContains(START_EVENT, null, START_EVENT_NAME)
      .doAssert(resultDto);
    // @formatter:on
  }

  @Test
  public void multipleVersionsRespectLatestNodesOnlyWhereLatestHasFewerNodes() {
    // given
    // @formatter:off
    IncidentDataDeployer.dataDeployer(incidentClient)
      .deployProcess(TWO_SEQUENTIAL_TASKS)
      .startProcessInstance()
      .withOpenIncident()
      .executeDeployment();
    IncidentDataDeployer.dataDeployer(incidentClient)
      .deployProcess(ONE_TASK)
      .startProcessInstance()
      .withOpenIncident()
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
      .measure(ViewProperty.FREQUENCY)
        .groupedByContains(END_EVENT, null, END_EVENT_NAME)
        .groupedByContains(SERVICE_TASK_ID_1, 2., SERVICE_TASK_NAME_1)
        .groupedByContains(START_EVENT, null, START_EVENT_NAME)
      .doAssert(resultDto);
    // @formatter:on
  }

  @Test
  public void reportEvaluationFilteredBySingleTenant() {
    // given
    final String tenantId1 = "tenantId1";
    final String tenantId2 = "tenantId2";
    final List<String> selectedTenants = Collections.singletonList(tenantId1);
    engineIntegrationExtension.createTenant(tenantId1);
    engineIntegrationExtension.createTenant(tenantId2);

    final ProcessInstanceEngineDto processInstanceEngineDto =
      incidentClient.deployAndStartProcessInstanceWithTenantAndWithOpenIncident(tenantId1);
    incidentClient.deployAndStartProcessInstanceWithTenantAndWithOpenIncident(tenantId2);
    incidentClient.deployAndStartProcessInstanceWithTenantAndWithOpenIncident(DEFAULT_TENANT);

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(
      processInstanceEngineDto.getProcessDefinitionKey(),
      ReportConstants.ALL_VERSIONS
    );
    reportData.setTenantIds(selectedTenants);
    ReportResultResponseDto<List<MapResultEntryDto>> resultDto = reportClient.evaluateMapReport(reportData).getResult();

    // then
    // @formatter:off
    MapResultAsserter.asserter()
      .processInstanceCount(1L)
      .measure(ViewProperty.FREQUENCY)
        .groupedByContains(END_EVENT, null, END_EVENT_NAME)
        .groupedByContains(SERVICE_TASK_ID_1, (double) selectedTenants.size(), SERVICE_TASK_NAME_1)
        .groupedByContains(START_EVENT, null, START_EVENT_NAME)
      .doAssert(resultDto);
    // @formatter:on
  }

  @Test
  public void worksWithFilter() {
    // given
    // @formatter:off
    IncidentDataDeployer.dataDeployer(incidentClient)
      .deployProcess(ONE_TASK)
      .startProcessInstance()
         // this will resolve the incident and complete the process instance
        .withResolvedIncident()
      .startProcessInstance()
        // this will leave the process instance running
        .withOpenIncident()
      .executeDeployment();
    // @formatter:on
    importAllEngineEntitiesFromScratch();

    // when I create a report without filters
    ProcessReportDataDto reportData = createReport(PROCESS_DEFINITION_KEY, "1");
    ReportResultResponseDto<List<MapResultEntryDto>> resultDto = reportClient.evaluateMapReport(reportData).getResult();

    // then the result has two process instances
    MapResultAsserter.asserter()
      .processInstanceCount(2L)
      .measure(ViewProperty.FREQUENCY)
      .groupedByContains(END_EVENT, null, END_EVENT_NAME)
      .groupedByContains(SERVICE_TASK_ID_1, 2., SERVICE_TASK_NAME_1)
      .groupedByContains(START_EVENT, null, START_EVENT_NAME)
      .doAssert(resultDto);

    // when creating a report with running instance filter
    List<ProcessFilterDto<?>> runningProcessInstanceFilter = ProcessFilterBuilder
      .filter()
      .runningInstancesOnly()
      .add()
      .buildList();
    reportData.getFilter().addAll(runningProcessInstanceFilter);
    resultDto = reportClient.evaluateMapReport(reportData).getResult();

    // then
    // @formatter:off
    MapResultAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.FREQUENCY)
        .groupedByContains(END_EVENT, null, END_EVENT_NAME)
        .groupedByContains(SERVICE_TASK_ID_1, 1., SERVICE_TASK_NAME_1)
        .groupedByContains(START_EVENT, null, START_EVENT_NAME)
      .doAssert(resultDto);
    // @formatter:on
  }

  private Stream<Arguments> instanceLevelIncidentFilter() {
    return Stream.of(
      Arguments.of(ProcessFilterBuilder.filter().withOpenIncident().filterLevel(INSTANCE).add().buildList(), 1.),
      Arguments.of(ProcessFilterBuilder.filter().withResolvedIncident().filterLevel(INSTANCE).add().buildList(), 1.),
      Arguments.of(ProcessFilterBuilder.filter().noIncidents().filterLevel(INSTANCE).add().buildList(), null)
    );
  }

  @ParameterizedTest
  @MethodSource("instanceLevelIncidentFilter")
  public void instanceLevelIncidentFilterIsAppliedAtInstanceLevel(final List<ProcessFilterDto<?>> filter,
                                                                  final Double expectedIncidentCount) {
    // given
    // @formatter:off
    IncidentDataDeployer.dataDeployer(incidentClient)
      .deployProcess(ONE_TASK)
      .startProcessInstance()
        .withResolvedIncident()
      .startProcessInstance()
        .withOpenIncident()
      .startProcessInstance()
        .withoutIncident()
      .executeDeployment();
    // @formatter:on
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(PROCESS_DEFINITION_KEY, "1");
    reportData.setFilter(filter);
    ReportResultResponseDto<List<MapResultEntryDto>> resultDto = reportClient.evaluateMapReport(reportData).getResult();

    // then
    // @formatter:off
    MapResultAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(3L)
      .measure(ViewProperty.FREQUENCY)
        .groupedByContains(END_EVENT, null, END_EVENT_NAME)
        .groupedByContains(SERVICE_TASK_ID_1, expectedIncidentCount, SERVICE_TASK_NAME_1)
        .groupedByContains(START_EVENT, null, START_EVENT_NAME)
      .doAssert(resultDto);
    // @formatter:on
  }

  private Stream<Arguments> viewLevelIncidentFilter() {
    return Stream.of(
      Arguments.of(
        ProcessFilterBuilder.filter().withOpenIncident().filterLevel(VIEW).add().buildList(), 1, null, 1.),
      Arguments.of(
        ProcessFilterBuilder.filter().withResolvedIncident().filterLevel(VIEW).add().buildList(), 2, 2., null)
    );
  }

  @ParameterizedTest
  @MethodSource("viewLevelIncidentFilter")
  public void viewLevelIncidentFilterIsAppliedAtViewLevel(final List<ProcessFilterDto<?>> filter,
                                                          final Integer expectedInstanceCount,
                                                          final Double firstExpectedResult,
                                                          final Double secondExpectedResult) {
    // given
    // @formatter:off
    IncidentDataDeployer.dataDeployer(incidentClient)
      .deployProcess(TWO_SEQUENTIAL_TASKS)
      .startProcessInstance()
        .withResolvedIncident()
      .startProcessInstance()
        .withResolvedAndOpenIncident()
      .startProcessInstance()
        .withoutIncident()
      .executeDeployment();
    // @formatter:on
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(PROCESS_DEFINITION_KEY, "1");
    reportData.setFilter(filter);
    ReportResultResponseDto<List<MapResultEntryDto>> resultDto = reportClient.evaluateMapReport(reportData).getResult();

    // then
    final MapResultAsserter asserter = MapResultAsserter.asserter()
      .processInstanceCount(expectedInstanceCount)
      .processInstanceCountWithoutFilters(3L);
    Optional.ofNullable(firstExpectedResult)
      .ifPresent(result -> asserter.measure(ViewProperty.FREQUENCY)
        .groupedByContains(SERVICE_TASK_ID_1, result, SERVICE_TASK_NAME_1)
        .add());
    Optional.ofNullable(secondExpectedResult)
      .ifPresent(result -> asserter.measure(ViewProperty.FREQUENCY)
        .groupedByContains(SERVICE_TASK_ID_2, result, SERVICE_TASK_NAME_2)
        .add());
    asserter.doAssert(resultDto);
  }

  @Test
  public void testCustomOrderOnResultKeyIsApplied() {
    // given
    // @formatter:off
    IncidentDataDeployer.dataDeployer(incidentClient)
      .deployProcess(TWO_PARALLEL_TASKS)
      .startProcessInstance()
        .withOpenIncident()
      .startProcessInstance()
        .withOpenIncident()
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
      .measure(ViewProperty.FREQUENCY)
        .groupedByContains(START_EVENT_ID, null, START_EVENT_NAME)
        .groupedByContains(SPLITTING_GATEWAY_ID, null)
        .groupedByContains(SERVICE_TASK_ID_2, 2., SERVICE_TASK_NAME_2)
        .groupedByContains(SERVICE_TASK_ID_1, 2., SERVICE_TASK_NAME_1)
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
      .startProcessInstance()
        .withOpenIncident()
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
      .measure(ViewProperty.FREQUENCY)
        .groupedByContains(SERVICE_TASK_ID_1, 2., SERVICE_TASK_NAME_1)
        .groupedByContains(SERVICE_TASK_ID_2, 1., SERVICE_TASK_NAME_2)
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
      .setReportDataType(INCIDENT_FREQ_GROUP_BY_FLOW_NODE)
      .build();
  }

}
