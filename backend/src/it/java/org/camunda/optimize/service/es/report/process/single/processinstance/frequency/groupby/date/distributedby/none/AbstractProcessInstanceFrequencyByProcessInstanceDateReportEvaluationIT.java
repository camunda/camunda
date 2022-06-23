/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.process.single.processinstance.frequency.groupby.date.distributedby.none;

import lombok.SneakyThrows;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.StartDateGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.value.DateGroupByValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.service.util.ProcessReportDataType;
import org.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.google.common.collect.Lists.newArrayList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnitMapper.mapToChronoUnit;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_KEY;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_VALUE;
import static org.camunda.optimize.test.util.DateModificationHelper.truncateToStartOfUnit;
import static org.camunda.optimize.util.BpmnModels.SERVICE_TASK_ID_1;

public abstract class AbstractProcessInstanceFrequencyByProcessInstanceDateReportEvaluationIT
  extends AbstractProcessDefinitionIT {

  protected abstract ProcessReportDataType getTestReportDataType();

  protected abstract ProcessGroupByType getGroupByType();

  protected abstract void changeProcessInstanceDate(final String processInstanceId,
                                                    final OffsetDateTime newDate) throws SQLException;

  protected ProcessDefinitionEngineDto deployTwoRunningAndOneCompletedUserTaskProcesses(final OffsetDateTime now) {
    final ProcessDefinitionEngineDto processDefinition = deploySimpleOneUserTasksDefinition();

    final ProcessInstanceEngineDto processInstance1 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(processInstance1.getId());
    final ProcessInstanceEngineDto processInstance2 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstance2.getId(), now.minusDays(1));
    final ProcessInstanceEngineDto processInstance3 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstance3.getId(), now.minusDays(2));
    return processDefinition;
  }

  protected abstract void updateProcessInstanceDates(Map<String, OffsetDateTime> newIdToDates) throws SQLException;

  @Test
  public void simpleReportEvaluation() {
    // given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder.createReportData()
      .setGroupByDateInterval(AggregateByDateUnit.DAY)
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setReportDataType(getTestReportDataType())
      .build();

    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(processInstanceDto.getProcessDefinitionKey());
    assertThat(resultReportDataDto.getDefinitionVersions()).contains(processInstanceDto.getProcessDefinitionVersion());
    assertThat(resultReportDataDto.getView()).isNotNull();
    assertThat(resultReportDataDto.getView().getEntity()).isEqualTo(ProcessViewEntity.PROCESS_INSTANCE);
    assertThat(resultReportDataDto.getView().getFirstProperty()).isEqualTo(ViewProperty.FREQUENCY);
    assertThat(resultReportDataDto.getGroupBy().getType()).isEqualTo(getGroupByType());
    assertThat(
      ((DateGroupByValueDto) resultReportDataDto.getGroupBy().getValue()).getUnit()).isEqualTo(AggregateByDateUnit.DAY);

    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluationResponse.getResult();
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).isNotNull().hasSize(1);
    ZonedDateTime startOfToday = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.DAYS);
    assertThat(resultData.get(0).getKey()).isEqualTo(localDateTimeToString(startOfToday));
    assertThat(resultData.get(0).getValue()).isEqualTo(1.);
  }

  @Test
  public void simpleReportEvaluationById() {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleServiceTaskProcess();
    importAllEngineEntitiesFromScratch();
    String reportId = createAndStoreDefaultReportDefinition(
      processInstance.getProcessDefinitionKey(), processInstance.getProcessDefinitionVersion()
    );

    // when
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReportById(reportId);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(processInstance.getProcessDefinitionKey());
    assertThat(resultReportDataDto.getDefinitionVersions()).contains(processInstance.getProcessDefinitionVersion());
    assertThat(resultReportDataDto.getView()).isNotNull();
    assertThat(resultReportDataDto.getView().getEntity()).isEqualTo(ProcessViewEntity.PROCESS_INSTANCE);
    assertThat(resultReportDataDto.getView().getFirstProperty()).isEqualTo(ViewProperty.FREQUENCY);
    assertThat(resultReportDataDto.getGroupBy().getType()).isEqualTo(getGroupByType());
    assertThat(((DateGroupByValueDto) resultReportDataDto.getGroupBy().getValue()).getUnit())
      .isEqualTo(AggregateByDateUnit.DAY);

    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluationResponse.getResult();
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).isNotNull().hasSize(1);
    ZonedDateTime startOfToday = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.DAYS);
    assertThat(resultData.get(0).getKey()).isEqualTo(localDateTimeToString(startOfToday));
    assertThat(resultData.get(0).getValue()).isEqualTo(1.);
  }

  @Test
  public void resultIsSortedInAscendingOrder() throws Exception {
    // given
    final OffsetDateTime referenceDate = OffsetDateTime.now();
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    final String definitionId = processInstanceDto.getDefinitionId();
    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(definitionId);
    changeProcessInstanceDate(processInstanceDto2.getId(), referenceDate.minusDays(2));
    final ProcessInstanceEngineDto processInstanceDto3 = engineIntegrationExtension.startProcessInstance(definitionId);
    changeProcessInstanceDate(processInstanceDto3.getId(), referenceDate.minusDays(1));

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder.createReportData()
      .setGroupByDateInterval(AggregateByDateUnit.DAY)
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setReportDataType(getTestReportDataType())
      .build();
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).hasSize(3);
    final List<String> resultKeys = resultData.stream().map(MapResultEntryDto::getKey).collect(Collectors.toList());
    // expect ascending order
    assertThat(resultKeys).isSortedAccordingTo(Comparator.naturalOrder());
  }

  @Test
  public void testCustomOrderOnResultKeyIsApplied() throws SQLException {
    // given
    final OffsetDateTime referenceDate = OffsetDateTime.now();
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    changeProcessInstanceDate(processInstanceDto.getId(), referenceDate);
    final String definitionId = processInstanceDto.getDefinitionId();
    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(definitionId);
    changeProcessInstanceDate(processInstanceDto2.getId(), referenceDate.minusDays(2));
    final ProcessInstanceEngineDto processInstanceDto3 = engineIntegrationExtension.startProcessInstance(definitionId);
    changeProcessInstanceDate(processInstanceDto3.getId(), referenceDate.minusDays(1));

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder.createReportData()
      .setGroupByDateInterval(AggregateByDateUnit.DAY)
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setReportDataType(getTestReportDataType())
      .build();
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_KEY, SortOrder.ASC));
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).hasSize(3);
    final List<String> resultKeys = resultData.stream().map(MapResultEntryDto::getKey).collect(Collectors.toList());
    assertThat(resultKeys)
      // expect ascending order
      .isSortedAccordingTo(Comparator.naturalOrder());
  }

  @Test
  public void testCustomOrderOnResultValueIsApplied() throws SQLException {
    // given
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    final String definitionId = processInstanceDto.getDefinitionId();
    final OffsetDateTime referenceDate = OffsetDateTime.now();
    changeProcessInstanceDate(processInstanceDto.getId(), referenceDate);
    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(definitionId);
    changeProcessInstanceDate(processInstanceDto2.getId(), referenceDate.minusDays(1));
    final ProcessInstanceEngineDto processInstanceDto3 = engineIntegrationExtension.startProcessInstance(definitionId);
    changeProcessInstanceDate(processInstanceDto3.getId(), referenceDate.minusDays(1));
    final ProcessInstanceEngineDto processInstanceDto4 = engineIntegrationExtension.startProcessInstance(definitionId);
    changeProcessInstanceDate(processInstanceDto4.getId(), referenceDate.minusDays(1));
    final ProcessInstanceEngineDto processInstanceDto5 = engineIntegrationExtension.startProcessInstance(definitionId);
    changeProcessInstanceDate(processInstanceDto5.getId(), referenceDate.minusDays(2));
    final ProcessInstanceEngineDto processInstanceDto6 = engineIntegrationExtension.startProcessInstance(definitionId);
    changeProcessInstanceDate(processInstanceDto6.getId(), referenceDate.minusDays(2));

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder.createReportData()
      .setGroupByDateInterval(AggregateByDateUnit.DAY)
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setReportDataType(getTestReportDataType())
      .build();
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_VALUE, SortOrder.DESC));
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).hasSize(3);
    final List<Double> resultValues = resultData.stream().map(MapResultEntryDto::getValue).collect(Collectors.toList());
    assertThat(resultValues).isSortedAccordingTo(Comparator.reverseOrder());
  }

  @Test
  public void processInstancesStartedAtSameIntervalAreGroupedTogether() throws Exception {
    // given
    final OffsetDateTime referenceDate = OffsetDateTime.now();

    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId());
    changeProcessInstanceDate(processInstanceDto.getId(), referenceDate);

    ProcessInstanceEngineDto processInstanceDto2 =
      engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId());
    changeProcessInstanceDate(processInstanceDto2.getId(), referenceDate.minusDays(1));

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder.createReportData()
      .setGroupByDateInterval(AggregateByDateUnit.DAY)
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setReportDataType(getTestReportDataType())
      .build();
    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).hasSize(2);
    ZonedDateTime startOfToday = truncateToStartOfUnit(referenceDate, ChronoUnit.DAYS);

    final String expectedStringToday = localDateTimeToString(startOfToday);
    final Optional<MapResultEntryDto> todayEntry = resultData.stream()
      .filter(e -> expectedStringToday.equals(e.getKey()))
      .findFirst();
    assertThat(todayEntry).isPresent().get().extracting(MapResultEntryDto::getValue).isEqualTo(2.);

    final String expectedStringYesterday = localDateTimeToString(startOfToday.minusDays(1));
    final Optional<MapResultEntryDto> yesterdayEntry = resultData.stream()
      .filter(e -> expectedStringYesterday.equals(e.getKey()))
      .findFirst();
    assertThat(yesterdayEntry).isPresent().get().extracting(MapResultEntryDto::getValue).isEqualTo(1.);
  }

  @Test
  public void emptyIntervalBetweenTwoProcessInstances() throws Exception {
    // given
    final OffsetDateTime referenceDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId());
    changeProcessInstanceDate(processInstanceDto.getId(), referenceDate);

    ProcessInstanceEngineDto processInstanceDto3 =
      engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId());
    changeProcessInstanceDate(processInstanceDto3.getId(), referenceDate.minusDays(2));

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder.createReportData()
      .setGroupByDateInterval(AggregateByDateUnit.DAY)
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setReportDataType(getTestReportDataType())
      .build();
    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).hasSize(3);

    ZonedDateTime startOfToday = truncateToStartOfUnit(referenceDate, ChronoUnit.DAYS);

    final String expectedStringToday = localDateTimeToString(startOfToday);
    final Optional<MapResultEntryDto> todayEntry = resultData.stream()
      .filter(e -> expectedStringToday.equals(e.getKey()))
      .findFirst();
    assertThat(todayEntry).isPresent().get().extracting(MapResultEntryDto::getValue).isEqualTo(2.);

    final String expectedStringYesterday = localDateTimeToString(startOfToday.minusDays(1));
    final Optional<MapResultEntryDto> yesterdayEntry = resultData.stream()
      .filter(e -> expectedStringYesterday.equals(e.getKey()))
      .findFirst();
    assertThat(yesterdayEntry).isPresent().get().extracting(MapResultEntryDto::getValue).isEqualTo(0.);

    final String expectedStringDayBeforeYesterday = localDateTimeToString(startOfToday.minusDays(2));
    final Optional<MapResultEntryDto> dayBeforeYesterdayEntry = resultData.stream()
      .filter(e -> expectedStringDayBeforeYesterday.equals(e.getKey()))
      .findFirst();
    assertThat(dayBeforeYesterdayEntry).isPresent().get().extracting(MapResultEntryDto::getValue).isEqualTo(1.);
  }

  @SneakyThrows
  @ParameterizedTest
  @MethodSource("staticAggregateByDateUnits")
  public void countGroupedByStaticDateUnit(final AggregateByDateUnit unit) {
    // given
    List<ProcessInstanceEngineDto> processInstanceDtos = deployAndStartSimpleProcesses(5);
    OffsetDateTime now = OffsetDateTime.now();
    updateProcessInstancesTime(processInstanceDtos, now, mapToChronoUnit(unit));

    importAllEngineEntitiesFromScratch();

    // when
    ProcessInstanceEngineDto processInstanceEngineDto = processInstanceDtos.get(0);
    ProcessReportDataDto reportData = createReportDataSortedDesc(
      processInstanceEngineDto.getProcessDefinitionKey(),
      processInstanceEngineDto.getProcessDefinitionVersion(),
      getTestReportDataType(),
      unit
    );
    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).isNotNull();
    assertStartDateResultMap(resultData, 5, now, mapToChronoUnit(unit));
  }

  @Test
  public void otherProcessDefinitionsDoNoAffectResult() {
    // given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    deployAndStartSimpleServiceTaskProcess();
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder.createReportData()
      .setGroupByDateInterval(AggregateByDateUnit.DAY)
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setReportDataType(getTestReportDataType())
      .build();
    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).isNotNull();
    assertStartDateResultMap(resultData, 1, OffsetDateTime.now(), ChronoUnit.DAYS);
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
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder.createReportData()
      .setGroupByDateInterval(AggregateByDateUnit.DAY)
      .setProcessDefinitionKey(processKey)
      .setProcessDefinitionVersion(ReportConstants.ALL_VERSIONS)
      .setReportDataType(getTestReportDataType())
      .build();

    reportData.setTenantIds(selectedTenants);
    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(selectedTenants.size());
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
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder.createReportData()
      .setGroupByDateInterval(AggregateByDateUnit.DAY)
      .setProcessDefinitionKey(processDefinition.getKey())
      .setProcessDefinitionVersion(String.valueOf(processDefinition.getVersion()))
      .setReportDataType(getTestReportDataType())
      .build();

    List<ProcessFilterDto<?>> flowNodeFilter = ProcessFilterBuilder.filter().executedFlowNodes()
      .id(SERVICE_TASK_ID_1)
      .add()
      .buildList();

    reportData.getFilter().addAll(flowNodeFilter);
    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getFirstMeasureData()).isNotNull().hasSize(1);
  }

  @Test
  public void optimizeExceptionOnGroupByTypeIsNull() {
    // given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    deployAndStartSimpleServiceTaskProcess();
    importAllEngineEntitiesFromScratch();

    ProcessReportDataDto dataDto = createGroupByStartDateReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      AggregateByDateUnit.DAY
    );
    dataDto.getGroupBy().setType(null);

    // when
    Response response = reportClient.evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void optimizeExceptionOnGroupByUnitIsNull() {
    // given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    deployAndStartSimpleServiceTaskProcess();
    importAllEngineEntitiesFromScratch();

    ProcessReportDataDto dataDto = createGroupByStartDateReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      AggregateByDateUnit.DAY
    );
    StartDateGroupByDto groupByDto = (StartDateGroupByDto) dataDto.getGroupBy();
    groupByDto.getValue().setUnit(null);

    // when
    Response response = reportClient.evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  private String createAndStoreDefaultReportDefinition(String processDefinitionKey, String processDefinitionVersion) {
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder.createReportData()
      .setGroupByDateInterval(AggregateByDateUnit.DAY)
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setReportDataType(getTestReportDataType())
      .build();
    return createNewReport(reportData);
  }

  private ProcessReportDataDto createGroupByStartDateReport(String processDefinitionKey,
                                                            String processDefinitionVersion,
                                                            AggregateByDateUnit groupByDateUnit) {
    return TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setGroupByDateInterval(groupByDateUnit)
      .setReportDataType(ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_START_DATE)
      .build();
  }

  private void assertStartDateResultMap(List<MapResultEntryDto> resultData,
                                        int size,
                                        OffsetDateTime now,
                                        ChronoUnit unit) {
    assertStartDateResultMap(resultData, size, now, unit, 1.);
  }

  protected void assertStartDateResultMap(List<MapResultEntryDto> resultData,
                                          int size,
                                          OffsetDateTime now,
                                          ChronoUnit unit,
                                          Double expectedValue) {
    assertThat(resultData).hasSize(size);
    final ZonedDateTime finalStartOfUnit = truncateToStartOfUnit(now, unit);
    IntStream.range(0, size)
      .forEach(i -> {
        final String expectedDateString = localDateTimeToString(finalStartOfUnit.minus((i), unit));
        assertThat(resultData.get(i).getKey()).isEqualTo(expectedDateString);
        assertThat(resultData.get(i).getValue()).isEqualTo(expectedValue);
      });
  }

  private void updateProcessInstancesTime(List<ProcessInstanceEngineDto> procInsts,
                                          OffsetDateTime now,
                                          ChronoUnit unit) throws SQLException {
    Map<String, OffsetDateTime> idToNewStartDate = new HashMap<>();
    IntStream.range(0, procInsts.size())
      .forEach(i -> {
        String id = procInsts.get(i).getId();
        OffsetDateTime newStartDate = now.minus(i, unit);
        idToNewStartDate.put(id, newStartDate);
      });
    updateProcessInstanceDates(idToNewStartDate);
  }

}