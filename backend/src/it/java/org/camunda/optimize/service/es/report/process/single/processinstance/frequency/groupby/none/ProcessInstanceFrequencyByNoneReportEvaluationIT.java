/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.process.single.processinstance.frequency.groupby.none;

import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.collect.Lists.newArrayList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.MAX;
import static org.camunda.optimize.service.util.ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_NONE;
import static org.camunda.optimize.util.BpmnModels.SERVICE_TASK_ID_1;

public class ProcessInstanceFrequencyByNoneReportEvaluationIT extends AbstractProcessDefinitionIT {

  public static final String PROCESS_DEFINITION_KEY = "123";

  @Test
  public void reportEvaluationForOneProcess() {
    // given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion()
    );
    AuthorizedProcessReportEvaluationResponseDto<Double> evaluationResponse =
      reportClient.evaluateNumberReport(reportData);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(processInstanceDto.getProcessDefinitionKey());
    assertThat(resultReportDataDto.getDefinitionVersions())
      .containsExactly(processInstanceDto.getProcessDefinitionVersion());
    assertThat(resultReportDataDto.getView()).isNotNull();
    assertThat(resultReportDataDto.getView().getEntity()).isEqualTo(ProcessViewEntity.PROCESS_INSTANCE);
    assertThat(resultReportDataDto.getView().getFirstProperty()).isEqualTo(ViewProperty.FREQUENCY);
    assertThat(resultReportDataDto.getGroupBy().getType()).isEqualTo(ProcessGroupByType.NONE);

    final ReportResultResponseDto<Double> resultDto = evaluationResponse.getResult();
    assertThat(resultDto.getInstanceCount()).isEqualTo(1L);
    assertThat(resultDto.getFirstMeasureData()).isNotNull();
    assertThat(resultDto.getFirstMeasureData()).isEqualTo(1.);
  }

  @Test
  public void evaluateNumberReportForMultipleInstances() {
    // given
    ProcessInstanceEngineDto engineDto = deployAndStartSimpleServiceTaskProcess(TEST_ACTIVITY);
    engineIntegrationExtension.startProcessInstance(engineDto.getDefinitionId());
    engineIntegrationExtension.startProcessInstance(engineDto.getDefinitionId());
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData =
      createReport(engineDto.getProcessDefinitionKey(), engineDto.getProcessDefinitionVersion());
    ReportResultResponseDto<Double> result = reportClient.evaluateNumberReport(reportData).getResult();

    // then
    assertThat(result.getFirstMeasureData()).isEqualTo(3.);
  }

  @Test
  public void otherProcessDefinitionsDoNoAffectResult() {
    // given
    ProcessInstanceEngineDto engineDto = deployAndStartSimpleServiceTaskProcess(TEST_ACTIVITY);
    engineIntegrationExtension.startProcessInstance(engineDto.getDefinitionId());
    deployAndStartSimpleServiceTaskProcess(TEST_ACTIVITY);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(
      engineDto.getProcessDefinitionKey(),
      engineDto.getProcessDefinitionVersion()
    );
    ReportResultResponseDto<Double> result = reportClient.evaluateNumberReport(reportData).getResult();

    // then
    assertThat(result.getFirstMeasureData()).isEqualTo(2.);
  }

  @Test
  public void reportEvaluationSingleBucketFilteredBySingleTenant() {
    // given
    final String tenantId1 = "tenantId1";
    final String tenantId2 = "tenantId2";
    final List<String> selectedTenants = newArrayList(tenantId1);
    final String processKey = deployAndStartMultiTenantSimpleServiceTaskProcess(
      newArrayList(null, tenantId1, tenantId2)
    );

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(processKey, ReportConstants.ALL_VERSIONS);
    reportData.setTenantIds(selectedTenants);
    ReportResultResponseDto<Double> result = reportClient.evaluateNumberReport(reportData).getResult();

    // then
    assertThat(result.getFirstMeasureData()).isEqualTo((double) selectedTenants.size());
  }

  @Test
  public void dateFilterInReport() {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleServiceTaskProcess();
    OffsetDateTime past = engineIntegrationExtension.getHistoricProcessInstance(processInstance.getId()).getStartTime();
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(
      processInstance.getProcessDefinitionKey(),
      processInstance.getProcessDefinitionVersion()
    );
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedInstanceStartDate()
                           .start(null)
                           .end(past.minusSeconds(1L))
                           .add()
                           .buildList());
    ReportResultResponseDto<Double> result = reportClient.evaluateNumberReport(reportData).getResult();

    // then
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getFirstMeasureData()).isEqualTo(0.);

    // when
    reportData = createReport(processInstance.getProcessDefinitionKey(), processInstance.getProcessDefinitionVersion());
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedInstanceStartDate()
                           .start(past)
                           .end(null)
                           .add()
                           .buildList());
    result = reportClient.evaluateNumberReport(reportData).getResult();

    // then
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getFirstMeasureData()).isEqualTo(1.);
  }

  @Test
  public void flowNodeFilterInReport() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("goToTask1", true);
    ProcessDefinitionEngineDto processDefinition = deploySimpleGatewayProcessDefinition();
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("goToTask1", false);
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData =
      createReport(processDefinition.getKey(), processDefinition.getVersionAsString());
    List<ProcessFilterDto<?>> flowNodeFilter = ProcessFilterBuilder
      .filter()
      .executedFlowNodes()
      .id(SERVICE_TASK_ID_1)
      .add()
      .buildList();
    reportData.getFilter().addAll(flowNodeFilter);
    ReportResultResponseDto<Double> result = reportClient.evaluateNumberReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1L);
  }

  @ParameterizedTest
  @MethodSource("viewLevelFilters")
  public void viewLevelFiltersOnlyAppliedToInstances(final List<ProcessFilterDto<?>> filtersToApply) {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcessAndGetDefinition();
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData =
      createReport(processDefinition.getKey(), processDefinition.getVersionAsString());
    reportData.getFilter().addAll(filtersToApply);
    ReportResultResponseDto<Double> result = reportClient.evaluateNumberReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isZero();
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(2L);
  }


  @Test
  public void optimizeExceptionOnViewPropertyIsNull() {
    // given
    ProcessReportDataDto dataDto =
      createReport(PROCESS_DEFINITION_KEY, "1");
    dataDto.getView().setProperties((ViewProperty) null);

    // when
    Response response = reportClient.evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void optimizeExceptionOnGroupByTypeIsNull() {
    // given
    ProcessReportDataDto dataDto =
      createReport(PROCESS_DEFINITION_KEY, "1");
    dataDto.getGroupBy().setType(null);

    // when
    Response response = reportClient.evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void frequencyReportCanAlsoBeEvaluatedIfAggregationTypeAndUserTaskDurationTimeIsDifferentFromDefault() {

    // given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion()
    );
    reportData.getConfiguration().setAggregationTypes(new AggregationDto(MAX));
    reportData.getConfiguration().setUserTaskDurationTimes(UserTaskDurationTime.IDLE);
    AuthorizedProcessReportEvaluationResponseDto<Double> evaluationResponse =
      reportClient.evaluateNumberReport(reportData);

    // then
    final ReportResultResponseDto<Double> resultDto = evaluationResponse.getResult();
    assertThat(resultDto.getInstanceCount()).isEqualTo(1L);
    assertThat(resultDto.getFirstMeasureData()).isNotNull();
    assertThat(resultDto.getFirstMeasureData()).isEqualTo(1.);
  }

  private ProcessReportDataDto createReport(String processDefinitionKey, String processDefinitionVersion) {
    return TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setReportDataType(PROC_INST_FREQ_GROUP_BY_NONE)
      .build();
  }

}
