/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.process.single.processinstance.percentage.groupby.none;

import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.test.util.DateCreationFreezer;
import org.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.google.common.collect.Lists.newArrayList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.IN;
import static org.camunda.optimize.service.util.ProcessReportDataType.PROC_INST_PER_GROUP_BY_NONE;

public class ProcessInstancePercentageByNoneReportEvaluationIT extends AbstractProcessDefinitionIT {

  public static final String PROCESS_DEFINITION_KEY = "123";

  @Test
  public void percentageReportEvaluationForOneProcess() {
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
    ProcessInstanceEngineDto engineDto = deployAndStartSimpleServiceTaskProcess();
    engineIntegrationExtension.startProcessInstance(engineDto.getDefinitionId());
    engineIntegrationExtension.startProcessInstance(engineDto.getDefinitionId());
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData =
      createReport(engineDto.getProcessDefinitionKey(), engineDto.getProcessDefinitionVersion());
    ReportResultResponseDto<Double> result = reportClient.evaluateNumberReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(3L);
    assertThat(result.getFirstMeasureData()).isEqualTo(100.);
  }

  @Test
  public void percentageReportEvaluationForZeroInstances() {
    // given
    final ProcessDefinitionEngineDto definition = deploySimpleOneUserTasksDefinition();
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(definition.getKey(), definition.getVersionAsString());
    ReportResultResponseDto<Double> result = reportClient.evaluateNumberReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(0L);
    assertThat(result.getFirstMeasureData()).isEqualTo(null);
  }

  @Test
  public void otherProcessDefinitionsNotInReportDoNotAffectResult() {
    // given
    ProcessInstanceEngineDto engineDto = deployAndStartSimpleServiceTaskProcess();
    engineIntegrationExtension.startProcessInstance(engineDto.getDefinitionId());
    deployAndStartSimpleServiceTaskProcess();
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(
      engineDto.getProcessDefinitionKey(),
      engineDto.getProcessDefinitionVersion()
    );
    ReportResultResponseDto<Double> result = reportClient.evaluateNumberReport(reportData).getResult();

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
    final String processKey = deployAndStartMultiTenantSimpleServiceTaskProcess(
      newArrayList(null, tenantId1, tenantId2)
    );

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(processKey, ReportConstants.ALL_VERSIONS);
    reportData.setTenantIds(selectedTenants);
    ReportResultResponseDto<Double> result = reportClient.evaluateNumberReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getFirstMeasureData()).isEqualTo(100.);
  }

  @Test
  public void instancesFilteredOutByInstanceFilter() {
    // given
    ProcessInstanceEngineDto engineDto = deployAndStartSimpleUserTaskProcess();
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.startProcessInstance(engineDto.getDefinitionId());
    engineIntegrationExtension.startProcessInstance(engineDto.getDefinitionId());
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData =
      createReport(engineDto.getProcessDefinitionKey(), engineDto.getProcessDefinitionVersion());
    reportData.setFilter(ProcessFilterBuilder.filter().completedInstancesOnly().add().buildList());
    ReportResultResponseDto<Double> result = reportClient.evaluateNumberReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(3L);
    assertThat(result.getFirstMeasureData()).isEqualTo(33.33333333333333);
  }

  @Test
  public void instanceFilteredOutByDateInstanceFilter() {
    // given
    final OffsetDateTime now = DateCreationFreezer.dateFreezer().freezeDateAndReturn();
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleServiceTaskProcess();
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstance.getId(), now.minusDays(1));
    engineIntegrationExtension.startProcessInstance(processInstance.getDefinitionId());
    engineIntegrationExtension.startProcessInstance(processInstance.getDefinitionId(), Map.of("varName", "varVal"));
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(
      processInstance.getProcessDefinitionKey(),
      processInstance.getProcessDefinitionVersion()
    );
    reportData.setFilter(
      ProcessFilterBuilder.filter()
        .fixedInstanceStartDate()
        .start(now.minusMinutes(10))
        .end(null)
        .add()
        .variable()
        .name("varName")
        .stringType()
        .values(Collections.singletonList("varVal"))
        .operator(IN)
        .add()
        .buildList());
    ReportResultResponseDto<Double> result = reportClient.evaluateNumberReport(reportData).getResult();
    final Integer storedInstanceCount = elasticSearchIntegrationTestExtension.getDocumentCountOf(
      new ProcessInstanceIndex(processInstance.getProcessDefinitionKey()).getIndexName());

    // then all three instance have been imported
    assertThat(storedInstanceCount).isEqualTo(3);
    // one is removed from baseline total as it doesn't match the instance level date filter
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(2L);
    // and only one matches the variable filter
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    // and the percentage is relative to the baseline total
    assertThat(result.getFirstMeasureData()).isEqualTo(50.);
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
    assertThat(result.getFirstMeasureData()).isEqualTo(0.);
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

  private ProcessReportDataDto createReport(String processDefinitionKey, String processDefinitionVersion) {
    return TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setReportDataType(PROC_INST_PER_GROUP_BY_NONE)
      .build();
  }

}
