/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.process.single.processinstance.percentage.groupby.none;

import static com.google.common.collect.Lists.newArrayList;
import static io.camunda.optimize.service.util.ProcessReportDataType.PROC_INST_PER_GROUP_BY_NONE;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.dto.optimize.ReportConstants;
import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import io.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResponseDto;
import io.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import io.camunda.optimize.service.db.es.report.process.AbstractProcessDefinitionITC8;
import io.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class ProcessInstancePercentageByNoneReportEvaluationIT
    extends AbstractProcessDefinitionITC8 {

  public static final String PROCESS_DEFINITION_KEY = "123";

  @Test
  public void percentageReportEvaluationForOneProcess() {
    // given
    final ProcessInstanceEvent processInstanceEvent = deployAndStartSimpleServiceTaskProcess();
    importAllZeebeEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData =
        createReport(
            String.valueOf(processInstanceEvent.getProcessDefinitionKey()),
            String.valueOf(processInstanceEvent.getVersion()));
    final AuthorizedProcessReportEvaluationResponseDto<Double> evaluationResponse =
        reportClient.evaluateNumberReport(reportData);

    // then
    final ProcessReportDataDto resultReportDataDto =
        evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey())
        .isEqualTo(processInstanceEvent.getProcessDefinitionKey());
    assertThat(resultReportDataDto.getDefinitionVersions())
        .containsExactly(String.valueOf(processInstanceEvent.getVersion()));
    assertThat(resultReportDataDto.getView()).isNotNull();
    assertThat(resultReportDataDto.getView().getEntity())
        .isEqualTo(ProcessViewEntity.PROCESS_INSTANCE);
    assertThat(resultReportDataDto.getView().getFirstProperty()).isEqualTo(ViewProperty.PERCENTAGE);
    assertThat(resultReportDataDto.getGroupBy().getType()).isEqualTo(ProcessGroupByType.NONE);

    final ReportResultResponseDto<Double> result = evaluationResponse.getResult();
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getFirstMeasureData()).isEqualTo(100.);
  }

  @Test
  public void percentageReportEvaluationForMultipleInstances() {
    // given
    final ProcessInstanceEvent processInstanceEvent = deployAndStartSimpleServiceTaskProcess();
    zeebeExtension.startProcessInstanceForProcess(processInstanceEvent.getBpmnProcessId());
    zeebeExtension.startProcessInstanceForProcess(processInstanceEvent.getBpmnProcessId());
    importAllZeebeEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData =
        createReport(
            String.valueOf(processInstanceEvent.getProcessDefinitionKey()),
            String.valueOf(processInstanceEvent.getVersion()));
    final ReportResultResponseDto<Double> result =
        reportClient.evaluateNumberReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(3L);
    assertThat(result.getFirstMeasureData()).isEqualTo(100.);
  }

  @Test
  public void percentageReportEvaluationForZeroInstances() {
    // given
    final var process = deploySimpleOneUserTasksDefinition();
    importAllZeebeEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData =
        createReport(
            String.valueOf(process.getProcessDefinitionKey()),
            String.valueOf(process.getVersion()));
    final ReportResultResponseDto<Double> result =
        reportClient.evaluateNumberReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isZero();
    assertThat(result.getFirstMeasureData()).isNull();
  }

  @Test
  public void otherProcessDefinitionsNotInReportDoNotAffectResult() {
    // given
    final var processInstanceEvent = deployAndStartSimpleServiceTaskProcess();
    zeebeExtension.startProcessInstanceForProcess(processInstanceEvent.getBpmnProcessId());
    deployAndStartSimpleServiceTaskProcess();
    importAllZeebeEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData =
        createReport(
            String.valueOf(processInstanceEvent.getProcessDefinitionKey()),
            String.valueOf(processInstanceEvent.getVersion()));
    final ReportResultResponseDto<Double> result =
        reportClient.evaluateNumberReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(2L);
    assertThat(result.getFirstMeasureData()).isEqualTo(100.);
  }

  @Test
  public void reportEvaluationOnlyConsidersSelectedTenants() {
    // given
    final String tenantId1 = "tenantId1";
    final String tenantId2 = "tenantId2";
    final List<String> selectedTenants = newArrayList(tenantId1);
    final String processKey =
        deployAndStartMultiTenantSimpleServiceTaskProcess(newArrayList(null, tenantId1, tenantId2));

    importAllZeebeEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processKey, ReportConstants.ALL_VERSIONS);
    reportData.setTenantIds(selectedTenants);
    final ReportResultResponseDto<Double> result =
        reportClient.evaluateNumberReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getFirstMeasureData()).isEqualTo(100.);
  }

  @Test
  public void instancesFilteredOutByInstanceFilter() {
    // given
    final var processInstanceEvent = deployAndStartSimpleUserTaskProcess();
    zeebeExtension.cancelProcessInstance(processInstanceEvent.getProcessInstanceKey());
    zeebeExtension.startProcessInstanceForProcess(processInstanceEvent.getBpmnProcessId());
    zeebeExtension.startProcessInstanceForProcess(processInstanceEvent.getBpmnProcessId());
    importAllZeebeEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData =
        createReport(
            String.valueOf(processInstanceEvent.getProcessDefinitionKey()),
            String.valueOf(processInstanceEvent.getVersion()));
    reportData.setFilter(ProcessFilterBuilder.filter().completedInstancesOnly().add().buildList());
    final ReportResultResponseDto<Double> result =
        reportClient.evaluateNumberReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(3L);
    assertThat(result.getFirstMeasureData()).isEqualTo(33.33333333333333);
  }

  @ParameterizedTest
  @MethodSource("viewLevelFilters")
  public void viewLevelFiltersOnlyAppliedToInstances(
      final List<ProcessFilterDto<?>> filtersToApply) {
    // given
    final var processInstanceEvent = deploySimpleServiceTaskProcessAndGetDefinition();
    zeebeExtension.startProcessInstanceForProcess(processInstanceEvent.getBpmnProcessId());
    zeebeExtension.startProcessInstanceForProcess(processInstanceEvent.getBpmnProcessId());
    importAllZeebeEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData =
        createReport(
            String.valueOf(processInstanceEvent.getProcessDefinitionKey()),
            String.valueOf(processInstanceEvent.getVersion()));
    reportData.getFilter().addAll(filtersToApply);
    final ReportResultResponseDto<Double> result =
        reportClient.evaluateNumberReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isZero();
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(2L);
    assertThat(result.getFirstMeasureData()).isEqualTo(0.);
  }

  @Test
  public void optimizeExceptionOnViewPropertyIsNull() {
    // given
    final ProcessReportDataDto dataDto = createReport(PROCESS_DEFINITION_KEY, "1");
    dataDto.getView().setProperties((ViewProperty) null);

    // when
    final Response response = reportClient.evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void optimizeExceptionOnGroupByTypeIsNull() {
    // given
    final ProcessReportDataDto dataDto = createReport(PROCESS_DEFINITION_KEY, "1");
    dataDto.getGroupBy().setType(null);

    // when
    final Response response = reportClient.evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  private ProcessReportDataDto createReport(
      final String processDefinitionKey, final String processDefinitionVersion) {
    return TemplatedProcessReportDataBuilder.createReportData()
        .setProcessDefinitionKey(processDefinitionKey)
        .setProcessDefinitionVersion(processDefinitionVersion)
        .setReportDataType(PROC_INST_PER_GROUP_BY_NONE)
        .build();
  }
}
