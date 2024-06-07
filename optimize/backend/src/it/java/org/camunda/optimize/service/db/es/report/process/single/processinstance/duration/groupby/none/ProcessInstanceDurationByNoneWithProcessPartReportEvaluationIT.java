/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.es.report.process.single.processinstance.duration.groupby.none;

import static com.google.common.collect.Lists.newArrayList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.service.util.ProcessReportDataType.PROC_INST_DUR_GROUP_BY_NONE_WITH_PART;
import static org.camunda.optimize.test.util.DurationAggregationUtil.getAggregationTypesAsListForProcessParts;
import static org.camunda.optimize.util.BpmnModels.END_LOOP;
import static org.camunda.optimize.util.BpmnModels.START_LOOP;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.measure.MeasureResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.db.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;

public class ProcessInstanceDurationByNoneWithProcessPartReportEvaluationIT
    extends AbstractProcessDefinitionIT {

  private static final String END_EVENT = "endEvent";
  private static final String START_EVENT = "startEvent";
  private static final String TEST_ACTIVITY = "testActivity";

  @Test
  public void reportEvaluationForOneProcess() {

    // given
    final OffsetDateTime startDate = OffsetDateTime.now();
    final OffsetDateTime endDate = startDate.plusSeconds(1);
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    engineDatabaseExtension.changeFlowNodeStartDatesForProcessDefinition(
        processInstanceDto.getDefinitionId(), startDate);
    engineDatabaseExtension.changeFlowNodeEndDatesForProcessDefinition(
        processInstanceDto.getDefinitionId(), endDate);
    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData =
        createReport(
            processInstanceDto.getProcessDefinitionKey(),
            processInstanceDto.getProcessDefinitionVersion(),
            START_EVENT,
            END_EVENT);

    final AuthorizedProcessReportEvaluationResponseDto<Double> evaluationResponse =
        reportClient.evaluateNumberReport(reportData);

    // then
    final ProcessReportDataDto resultReportDataDto =
        evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey())
        .isEqualTo(processInstanceDto.getProcessDefinitionKey());
    assertThat(resultReportDataDto.getDefinitionVersions())
        .containsExactly(processInstanceDto.getProcessDefinitionVersion());
    assertThat(resultReportDataDto.getView()).isNotNull();
    assertThat(resultReportDataDto.getView().getEntity())
        .isEqualTo(ProcessViewEntity.PROCESS_INSTANCE);
    assertThat(resultReportDataDto.getView().getFirstProperty()).isEqualTo(ViewProperty.DURATION);
    assertThat(resultReportDataDto.getGroupBy().getType()).isEqualTo(ProcessGroupByType.NONE);
    assertThat(resultReportDataDto.getConfiguration().getProcessPart()).isPresent();

    assertThat(evaluationResponse.getResult().getInstanceCount()).isEqualTo(1L);
    final Double calculatedResult = evaluationResponse.getResult().getFirstMeasureData();
    assertThat(calculatedResult).isEqualTo(1000.);
  }

  @Test
  public void reportEvaluationForOneProcessBigActivityDuration() {

    // given
    final OffsetDateTime startDate = OffsetDateTime.now();
    // big activity durations in ms (>32 bit) caused the bug described in OPT-2393
    final long activityDurationInSeconds = Integer.valueOf(Integer.MAX_VALUE).longValue();
    final OffsetDateTime endDate = startDate.plusSeconds(activityDurationInSeconds);
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    engineDatabaseExtension.changeFlowNodeStartDatesForProcessDefinition(
        processInstanceDto.getDefinitionId(), startDate);
    engineDatabaseExtension.changeFlowNodeEndDatesForProcessDefinition(
        processInstanceDto.getDefinitionId(), endDate);
    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData =
        createReport(
            processInstanceDto.getProcessDefinitionKey(),
            processInstanceDto.getProcessDefinitionVersion(),
            START_EVENT,
            END_EVENT);

    final AuthorizedProcessReportEvaluationResponseDto<Double> evaluationResponse =
        reportClient.evaluateNumberReport(reportData);

    // then
    assertThat(evaluationResponse.getResult().getInstanceCount()).isEqualTo(1L);
    final Double calculatedResult = evaluationResponse.getResult().getFirstMeasureData();
    assertThat(calculatedResult).isEqualTo(activityDurationInSeconds * 1000.);
  }

  @Test
  public void reportEvaluationById() {
    // given
    final OffsetDateTime startDate = OffsetDateTime.now();
    final OffsetDateTime endDate = startDate.plusSeconds(1);
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    engineDatabaseExtension.changeFlowNodeStartDatesForProcessDefinition(
        processInstanceDto.getDefinitionId(), startDate);
    engineDatabaseExtension.changeFlowNodeEndDatesForProcessDefinition(
        processInstanceDto.getDefinitionId(), endDate);
    importAllEngineEntitiesFromScratch();
    final ProcessReportDataDto reportDataDto =
        createReport(
            processInstanceDto.getProcessDefinitionKey(),
            processInstanceDto.getProcessDefinitionVersion(),
            START_EVENT,
            END_EVENT);

    final String reportId = createNewReport(reportDataDto);

    // when
    final AuthorizedProcessReportEvaluationResponseDto<Double> evaluationResponse =
        reportClient.evaluateNumberReportById(reportId);

    // then
    final ProcessReportDataDto resultReportDataDto =
        evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey())
        .isEqualTo(processInstanceDto.getProcessDefinitionKey());
    assertThat(resultReportDataDto.getDefinitionVersions())
        .containsExactly(processInstanceDto.getProcessDefinitionVersion());
    assertThat(resultReportDataDto.getView()).isNotNull();
    assertThat(resultReportDataDto.getView().getEntity())
        .isEqualTo(ProcessViewEntity.PROCESS_INSTANCE);
    assertThat(resultReportDataDto.getView().getFirstProperty()).isEqualTo(ViewProperty.DURATION);
    assertThat(resultReportDataDto.getGroupBy().getType()).isEqualTo(ProcessGroupByType.NONE);
    assertThat(resultReportDataDto.getConfiguration().getProcessPart()).isPresent();

    final Double calculatedResult = evaluationResponse.getResult().getFirstMeasureData();
    assertThat(calculatedResult).isEqualTo(1000.);
  }

  @Test
  public void evaluateReportForMultipleEventsWithAllAggregationTypes() {
    // given
    final OffsetDateTime startDate = OffsetDateTime.now();
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    final ProcessInstanceEngineDto processInstanceDto2 =
        engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId());
    final ProcessInstanceEngineDto processInstanceDto3 =
        engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId());
    final Map<String, OffsetDateTime> startDatesToUpdate = new HashMap<>();
    startDatesToUpdate.put(processInstanceDto.getId(), startDate);
    startDatesToUpdate.put(processInstanceDto2.getId(), startDate);
    startDatesToUpdate.put(processInstanceDto3.getId(), startDate);
    engineDatabaseExtension.changeAllFlowNodeStartDates(startDatesToUpdate);
    final Map<String, OffsetDateTime> endDatesToUpdate = new HashMap<>();
    endDatesToUpdate.put(processInstanceDto.getId(), startDate.plusSeconds(1));
    endDatesToUpdate.put(processInstanceDto2.getId(), startDate.plusSeconds(2));
    endDatesToUpdate.put(processInstanceDto3.getId(), startDate.plusSeconds(9));
    engineDatabaseExtension.changeAllFlowNodeEndDates(endDatesToUpdate);
    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData =
        createReport(
            processInstanceDto.getProcessDefinitionKey(),
            processInstanceDto.getProcessDefinitionVersion(),
            START_EVENT,
            END_EVENT);
    reportData.getConfiguration().setAggregationTypes(getAggregationTypesAsListForProcessParts());

    final AuthorizedProcessReportEvaluationResponseDto<Double> evaluationResponse =
        reportClient.evaluateNumberReport(reportData);

    // then
    assertAggregationResults(evaluationResponse, 1000., 2000., 9000.);
  }

  @Test
  public void takeCorrectActivityOccurrences() {
    // given
    final OffsetDateTime startDate = OffsetDateTime.now().minusHours(1);
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartLoopingProcess();
    engineDatabaseExtension.changeFirstFlowNodeInstanceStartDate(START_LOOP, startDate);
    engineDatabaseExtension.changeFirstFlowNodeInstanceEndDate(END_LOOP, startDate.plusSeconds(2));
    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData =
        createReport(
            processInstanceDto.getProcessDefinitionKey(),
            processInstanceDto.getProcessDefinitionVersion(),
            START_LOOP,
            END_LOOP);

    final ReportResultResponseDto<Double> resultDto =
        reportClient.evaluateNumberReport(reportData).getResult();

    // then
    final Double calculatedResult = resultDto.getFirstMeasureData();
    assertThat(calculatedResult).isEqualTo(2000.);
  }

  /**
   * When migrating from Optimize 2.1 to 2.2 then all the activity instances that were imported in
   * 2.1 don't have a start and end date. This test ensures that Optimize can cope with that.
   */
  @Test
  public void activityHasNullDates() {
    // given
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    importAllEngineEntitiesFromScratch();
    setActivityStartDatesToNull(processInstanceDto.getProcessDefinitionKey());

    // when
    final ProcessReportDataDto reportData =
        createReport(
            processInstanceDto.getProcessDefinitionKey(),
            processInstanceDto.getProcessDefinitionVersion(),
            START_EVENT,
            END_EVENT);

    final ReportResultResponseDto<Double> resultDto =
        reportClient.evaluateNumberReport(reportData).getResult();

    // then
    final Double calculatedResult = resultDto.getFirstMeasureData();
    assertThat(calculatedResult).isNull();
  }

  private void setActivityStartDatesToNull(final String processDefinitionKey) {
    databaseIntegrationTestExtension.setActivityStartDatesToNull(processDefinitionKey);
  }

  @Test
  public void firstOccurrenceOfEndDateIsBeforeFirstOccurrenceOfStartDate() {
    // given
    final OffsetDateTime startDate = OffsetDateTime.now().minusHours(1);
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    engineDatabaseExtension.changeFirstFlowNodeInstanceStartDate(START_EVENT, startDate);
    engineDatabaseExtension.changeFirstFlowNodeInstanceEndDate(
        END_EVENT, startDate.minusSeconds(2));
    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData =
        createReport(
            processInstanceDto.getProcessDefinitionKey(),
            processInstanceDto.getProcessDefinitionVersion(),
            START_EVENT,
            END_EVENT);

    final ReportResultResponseDto<Double> resultDto =
        reportClient.evaluateNumberReport(reportData).getResult();

    // then
    final Double calculatedResult = resultDto.getFirstMeasureData();
    assertThat(calculatedResult).isNull();
  }

  @Test
  public void unknownStartReturnsZero() {
    // given
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    engineDatabaseExtension.changeFlowNodeEndDatesForProcessDefinition(
        processInstanceDto.getDefinitionId(), OffsetDateTime.now().plusHours(1));
    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData =
        createReport(
            processInstanceDto.getProcessDefinitionKey(),
            processInstanceDto.getProcessDefinitionVersion(),
            "FOoO",
            END_EVENT);

    final ReportResultResponseDto<Double> resultDto =
        reportClient.evaluateNumberReport(reportData).getResult();

    // then
    final Double calculatedResult = resultDto.getFirstMeasureData();
    assertThat(resultDto.getInstanceCount()).isEqualTo(0L);
    assertThat(calculatedResult).isNull();
  }

  @Test
  public void unknownEndReturnsZero() {
    // given
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    engineDatabaseExtension.changeFlowNodeStartDatesForProcessDefinition(
        processInstanceDto.getDefinitionId(), OffsetDateTime.now().minusHours(1));
    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData =
        createReport(
            processInstanceDto.getProcessDefinitionKey(),
            processInstanceDto.getProcessDefinitionVersion(),
            START_EVENT,
            "FOO");

    final ReportResultResponseDto<Double> resultDto =
        reportClient.evaluateNumberReport(reportData).getResult();

    // then
    final Double calculatedResult = resultDto.getFirstMeasureData();
    assertThat(resultDto.getInstanceCount()).isEqualTo(0L);
    assertThat(calculatedResult).isNull();
  }

  @Test
  public void noAvailableProcessInstancesReturnsNull() {
    // when
    final ProcessReportDataDto reportData = createReport("FOOPROCDEF", "1", START_EVENT, END_EVENT);

    final ReportResultResponseDto<Double> resultDto =
        reportClient.evaluateNumberReport(reportData).getResult();

    // then
    final Double calculatedResult = resultDto.getFirstMeasureData();
    assertThat(resultDto.getInstanceCount()).isEqualTo(0L);
    assertThat(calculatedResult).isNull();
  }

  @Test
  public void otherProcessDefinitionsDoNoAffectResult() {
    // given
    final OffsetDateTime startDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();

    final String processDefinitionKey = processInstanceDto.getProcessDefinitionKey();
    final String processDefinitionVersion = processInstanceDto.getProcessDefinitionVersion();

    engineDatabaseExtension.changeAllFlowNodeStartDates(processInstanceDto.getId(), startDate);
    engineDatabaseExtension.changeAllFlowNodeEndDates(
        processInstanceDto.getId(), startDate.plusSeconds(1));
    processInstanceDto =
        engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId());
    engineDatabaseExtension.changeAllFlowNodeStartDates(processInstanceDto.getId(), startDate);
    engineDatabaseExtension.changeAllFlowNodeEndDates(
        processInstanceDto.getId(), startDate.plusSeconds(9));
    processInstanceDto =
        engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId());
    engineDatabaseExtension.changeAllFlowNodeStartDates(processInstanceDto.getId(), startDate);
    engineDatabaseExtension.changeAllFlowNodeEndDates(
        processInstanceDto.getId(), startDate.plusSeconds(2));
    deployAndStartSimpleServiceTaskProcess();
    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData =
        createReport(processDefinitionKey, processDefinitionVersion, START_EVENT, END_EVENT);

    reportData.getConfiguration().setAggregationTypes(getAggregationTypesAsListForProcessParts());
    final AuthorizedProcessReportEvaluationResponseDto<Double> evaluationResponse =
        reportClient.evaluateNumberReport(reportData);

    // then
    assertAggregationResults(evaluationResponse, 1000., 9000., 2000.);
  }

  @Test
  public void reportEvaluationSingleBucketFilteredBySingleTenant() {
    // given
    final String tenantId1 = "tenantId1";
    final String tenantId2 = "tenantId2";
    final List<String> selectedTenants = newArrayList(tenantId1);
    final String processKey =
        deployAndStartMultiTenantSimpleServiceTaskProcess(newArrayList(null, tenantId1, tenantId2));

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData =
        createReport(processKey, ALL_VERSIONS, START_EVENT, END_EVENT);
    reportData.setTenantIds(selectedTenants);
    final ReportResultResponseDto<Double> result =
        reportClient.evaluateNumberReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo((long) selectedTenants.size());
  }

  @Test
  public void filterInReportWorks() {
    // given
    final Map<String, Object> variables = new HashMap<>();
    variables.put("var", true);
    final OffsetDateTime startDate = OffsetDateTime.now();
    final ProcessInstanceEngineDto processInstanceDto =
        deployAndStartSimpleServiceTaskProcessWithVariables(variables);
    engineDatabaseExtension.changeAllFlowNodeStartDates(processInstanceDto.getId(), startDate);
    engineDatabaseExtension.changeAllFlowNodeEndDates(
        processInstanceDto.getId(), startDate.plusSeconds(1));
    final String processDefinitionId = processInstanceDto.getDefinitionId();
    engineIntegrationExtension.startProcessInstance(processDefinitionId);
    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData =
        createReport(
            processInstanceDto.getProcessDefinitionKey(),
            processInstanceDto.getProcessDefinitionVersion(),
            START_EVENT,
            END_EVENT);
    reportData.setFilter(createVariableFilter("true"));
    ReportResultResponseDto<Double> resultDto =
        reportClient.evaluateNumberReport(reportData).getResult();

    // then
    Double calculatedResult = resultDto.getFirstMeasureData();
    assertThat(calculatedResult).isEqualTo(1000.);

    // when
    reportData.setFilter(createVariableFilter("false"));
    resultDto = reportClient.evaluateNumberReport(reportData).getResult();

    // then
    calculatedResult = resultDto.getFirstMeasureData();
    assertThat(resultDto.getInstanceCount()).isEqualTo(0L);
    assertThat(calculatedResult).isNull();
  }

  private List<ProcessFilterDto<?>> createVariableFilter(final String value) {
    return ProcessFilterBuilder.filter()
        .variable()
        .booleanType()
        .values(Collections.singletonList(value))
        .name("var")
        .add()
        .buildList();
  }

  private ProcessInstanceEngineDto deployAndStartSimpleServiceTaskProcessWithVariables(
      final Map<String, Object> variables) {
    final BpmnModelInstance processModel =
        Bpmn.createExecutableProcess("aProcess")
            .name("aProcessName")
            .startEvent(START_EVENT)
            .serviceTask(TEST_ACTIVITY)
            .camundaExpression("${true}")
            .endEvent(END_EVENT)
            .done();
    return engineIntegrationExtension.deployAndStartProcessWithVariables(processModel, variables);
  }

  private void assertAggregationResults(
      final AuthorizedProcessReportEvaluationResponseDto<Double> evaluationResponse,
      final Number... durationsToCalculate) {
    final Map<AggregationDto, Double> expectedAggregationResults =
        databaseIntegrationTestExtension.calculateExpectedValueGivenDurations(durationsToCalculate);
    final Map<AggregationDto, Double> resultByAggregationType =
        evaluationResponse.getResult().getMeasures().stream()
            .collect(
                Collectors.toMap(
                    MeasureResponseDto::getAggregationType, MeasureResponseDto::getData));
    assertThat(resultByAggregationType.entrySet())
        .allSatisfy(entry -> assertThat(expectedAggregationResults.entrySet()).contains(entry));
  }

  private ProcessReportDataDto createReport(
      final String definitionKey,
      final String definitionVersion,
      final String start,
      final String end) {
    return TemplatedProcessReportDataBuilder.createReportData()
        .setProcessDefinitionKey(definitionKey)
        .setProcessDefinitionVersion(definitionVersion)
        .setStartFlowNodeId(start)
        .setEndFlowNodeId(end)
        .setReportDataType(PROC_INST_DUR_GROUP_BY_NONE_WITH_PART)
        .build();
  }
}
