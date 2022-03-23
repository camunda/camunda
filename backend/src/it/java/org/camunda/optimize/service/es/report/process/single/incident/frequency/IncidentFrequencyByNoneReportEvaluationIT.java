/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.process.single.incident.frequency;

import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.OpenIncidentFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ResolvedIncidentFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.service.es.report.process.single.incident.duration.IncidentDataDeployer;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel.INSTANCE;
import static org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel.VIEW;
import static org.camunda.optimize.service.es.report.process.single.incident.duration.IncidentDataDeployer.IncidentProcessType.ONE_TASK;
import static org.camunda.optimize.test.optimize.CollectionClient.DEFAULT_TENANT;
import static org.camunda.optimize.test.util.ProcessReportDataType.INCIDENT_FREQ_GROUP_BY_NONE;
import static org.camunda.optimize.util.BpmnModels.SERVICE_TASK_ID_2;
import static org.camunda.optimize.util.BpmnModels.getTwoExternalTaskProcess;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class IncidentFrequencyByNoneReportEvaluationIT extends AbstractProcessDefinitionIT {

  private Stream<Supplier<ProcessInstanceEngineDto>> startInstanceWithDifferentIncidents() {
    return Stream.of(
      () -> incidentClient.deployAndStartProcessInstanceWithTwoOpenIncidents(),
      () -> incidentClient.deployAndStartProcessInstanceWithTwoResolvedIncidents(),
      () -> incidentClient.deployAndStartProcessInstanceWithDeletedResolvedIncidents()
    );
  }

  @ParameterizedTest
  @MethodSource("startInstanceWithDifferentIncidents")
  public void twoIncidentsInOneProcessInstance(Supplier<ProcessInstanceEngineDto> startAndReturnProcessInstanceWithTwoIncidents) {
    // given
    final ProcessInstanceEngineDto processInstance = startAndReturnProcessInstanceWithTwoIncidents.get();
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(
      processInstance.getProcessDefinitionKey(),
      processInstance.getProcessDefinitionVersion()
    );
    AuthorizedProcessReportEvaluationResponseDto<Double> evaluationResponse =
      reportClient.evaluateNumberReport(reportData);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(processInstance.getProcessDefinitionKey());
    assertThat(resultReportDataDto.getDefinitionVersions())
      .containsExactly(processInstance.getProcessDefinitionVersion());
    assertThat(resultReportDataDto.getView()).isNotNull();
    assertThat(resultReportDataDto.getView().getEntity()).isEqualTo(ProcessViewEntity.INCIDENT);
    assertThat(resultReportDataDto.getView().getFirstProperty()).isEqualTo(ViewProperty.FREQUENCY);
    assertThat(resultReportDataDto.getGroupBy().getType()).isEqualTo(ProcessGroupByType.NONE);

    final ReportResultResponseDto<Double> resultDto = evaluationResponse.getResult();
    assertThat(resultDto.getInstanceCount()).isEqualTo(1L);
    assertThat(resultDto.getFirstMeasureData()).isNotNull();
    assertThat(resultDto.getFirstMeasureData()).isEqualTo(2.);
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
    final ReportResultResponseDto<Double> resultDto = reportClient.evaluateNumberReport(reportData).getResult();

    // then
    assertThat(resultDto.getInstanceCount()).isEqualTo(2L);
    assertThat(resultDto.getFirstMeasureData()).isNotNull();
    assertThat(resultDto.getFirstMeasureData()).isEqualTo(2.);
  }

  @Test
  public void incidentsForMultipleProcessInstances() {
    // given
    final ProcessInstanceEngineDto processInstance = incidentClient.deployAndStartProcessInstanceWithOpenIncident();
    incidentClient.startProcessInstanceAndCreateOpenIncident(processInstance.getDefinitionId());
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(
      processInstance.getProcessDefinitionKey(),
      processInstance.getProcessDefinitionVersion()
    );
    final ReportResultResponseDto<Double> resultDto =
      reportClient.evaluateNumberReport(reportData).getResult();

    // then
    assertThat(resultDto.getInstanceCount()).isEqualTo(2L);
    assertThat(resultDto.getFirstMeasureData()).isNotNull();
    assertThat(resultDto.getFirstMeasureData()).isEqualTo(2.);
  }

  @Test
  public void differentIncidentTypesInTheSameReport() {
    // given
    final ProcessInstanceEngineDto processInstance = incidentClient.deployAndStartProcessInstanceWithResolvedIncident();
    incidentClient.startProcessInstanceAndCreateOpenIncident(processInstance.getDefinitionId());
    incidentClient.startProcessInstanceAndCreateDeletedIncident(processInstance.getDefinitionId());
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(
      processInstance.getProcessDefinitionKey(),
      processInstance.getProcessDefinitionVersion()
    );
    final ReportResultResponseDto<Double> resultDto =
      reportClient.evaluateNumberReport(reportData).getResult();

    // then
    assertThat(resultDto.getInstanceCount()).isEqualTo(3L);
    assertThat(resultDto.getFirstMeasureData()).isNotNull();
    assertThat(resultDto.getFirstMeasureData()).isEqualTo(3.);
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
    final ReportResultResponseDto<Double> resultDto =
      reportClient.evaluateNumberReport(reportData).getResult();

    // then
    assertThat(resultDto.getInstanceCount()).isEqualTo(1L);
    assertThat(resultDto.getFirstMeasureData()).isNotNull();
    assertThat(resultDto.getFirstMeasureData()).isEqualTo(1.);
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
    final ReportResultResponseDto<Double> resultDto =
      reportClient.evaluateNumberReport(reportData).getResult();

    // then
    assertThat(resultDto.getInstanceCount()).isEqualTo(2L);
    assertThat(resultDto.getFirstMeasureData()).isNotNull();
    assertThat(resultDto.getFirstMeasureData()).isEqualTo(2.);
  }

  @Test
  public void reportEvaluationSingleBucketFilteredBySingleTenant() {
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
    ReportResultResponseDto<Double> result = reportClient.evaluateNumberReport(reportData).getResult();

    // then
    assertThat(result.getFirstMeasureData()).isEqualTo((double) selectedTenants.size());
  }

  @Test
  public void flowNodeFilterInReport() {
    // given two process instances
    // Instance 1: one incident in task 1 (resolved) and one incident in task 2 (open)
    // Instance 2: one incident in task 1 (open) and because of that the task is still pending.
    final ProcessInstanceEngineDto processInstanceEngineDto =
      engineIntegrationExtension.deployAndStartProcess(getTwoExternalTaskProcess());
    incidentClient.createOpenIncidentForInstancesWithBusinessKey(processInstanceEngineDto.getBusinessKey());
    incidentClient.resolveOpenIncidents(processInstanceEngineDto.getId());
    incidentClient.createOpenIncidentForInstancesWithBusinessKey(processInstanceEngineDto.getBusinessKey());
    final ProcessInstanceEngineDto processInstanceEngineDto2 =
      engineIntegrationExtension.startProcessInstance(processInstanceEngineDto.getDefinitionId());
    incidentClient.createOpenIncidentForInstancesWithBusinessKey(processInstanceEngineDto2.getBusinessKey());

    importAllEngineEntitiesFromScratch();

    // when I create a report without filters
    ProcessReportDataDto reportData =
      createReport(
        processInstanceEngineDto.getProcessDefinitionKey(),
        processInstanceEngineDto.getProcessDefinitionVersion()
      );
    ReportResultResponseDto<Double> result = reportClient.evaluateNumberReport(reportData).getResult();

    // then the result has two process instances
    assertThat(result.getInstanceCount()).isEqualTo(2L);
    assertThat(result.getFirstMeasureData()).isEqualTo(3.);

    // when I create a flow node filter on task 2
    List<ProcessFilterDto<?>> flowNodeFilter = ProcessFilterBuilder
      .filter()
      .executedFlowNodes()
      .id(SERVICE_TASK_ID_2)
      .add()
      .buildList();
    reportData.getFilter().addAll(flowNodeFilter);
    result = reportClient.evaluateNumberReport(reportData).getResult();

    // then we only get instance 1 because it's the only instance that
    // has executed (which includes pending) the task 2.
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getFirstMeasureData()).isEqualTo(2.);
  }

  private Stream<Arguments> filterAndExpectedResult() {
    return Stream.of(
      Arguments.of(ProcessFilterBuilder.filter().withOpenIncident().filterLevel(INSTANCE).add().buildList(), 2.),
      Arguments.of(ProcessFilterBuilder.filter().withOpenIncident().filterLevel(VIEW).add().buildList(), 1.),
      Arguments.of(ProcessFilterBuilder.filter().withResolvedIncident().filterLevel(INSTANCE).add().buildList(), 2.),
      Arguments.of(ProcessFilterBuilder.filter().withResolvedIncident().filterLevel(VIEW).add().buildList(), 1.),
      Arguments.of(ProcessFilterBuilder.filter().noIncidents().filterLevel(INSTANCE).add().buildList(), 0.)
    );
  }

  @ParameterizedTest
  @MethodSource("filterAndExpectedResult")
  public void incidentFilterIsAppliedAtCorrectLevel(final List<ProcessFilterDto<?>> filter,
                                                    final Double expectedResult) {
    // given
    final ProcessInstanceEngineDto firstInstance =
      engineIntegrationExtension.deployAndStartProcess(getTwoExternalTaskProcess());
    incidentClient.createOpenIncidentForInstancesWithBusinessKey(firstInstance.getBusinessKey());
    incidentClient.resolveOpenIncidents(firstInstance.getId());
    incidentClient.createOpenIncidentForInstancesWithBusinessKey(firstInstance.getBusinessKey());
    // the second instance has no incidents
    engineIntegrationExtension.startProcessInstance(firstInstance.getDefinitionId());

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData =
      createReport(firstInstance.getProcessDefinitionKey(), firstInstance.getProcessDefinitionVersion());
    reportData.setFilter(filter);
    ReportResultResponseDto<Double> result = reportClient.evaluateNumberReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(2L);
    assertThat(result.getFirstMeasureData()).isEqualTo(expectedResult);
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
  public void multiLevelFiltersOnlyAppliedToInstances(final List<ProcessFilterDto<?>> filtersToApply) {
    final ProcessInstanceEngineDto processInstanceEngineDto =
      engineIntegrationExtension.deployAndStartProcess(getTwoExternalTaskProcess());
    incidentClient.createOpenIncidentForInstancesWithBusinessKey(processInstanceEngineDto.getBusinessKey());
    incidentClient.resolveOpenIncidents(processInstanceEngineDto.getId());
    incidentClient.createOpenIncidentForInstancesWithBusinessKey(processInstanceEngineDto.getBusinessKey());
    final ProcessInstanceEngineDto processInstanceEngineDto2 =
      engineIntegrationExtension.startProcessInstance(processInstanceEngineDto.getDefinitionId());
    incidentClient.createOpenIncidentForInstancesWithBusinessKey(processInstanceEngineDto2.getBusinessKey());

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData =
      createReport(
        processInstanceEngineDto.getProcessDefinitionKey(),
        processInstanceEngineDto.getProcessDefinitionVersion()
      );
    reportData.getFilter().addAll(filtersToApply);
    final ReportResultResponseDto<Double> result = reportClient.evaluateNumberReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isZero();
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(2L);
  }

  private ProcessReportDataDto createReport(String processDefinitionKey, String processDefinitionVersion) {
    return TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setReportDataType(INCIDENT_FREQ_GROUP_BY_NONE)
      .build();
  }

}
