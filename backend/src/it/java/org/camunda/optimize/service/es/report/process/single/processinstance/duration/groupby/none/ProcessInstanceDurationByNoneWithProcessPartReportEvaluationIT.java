/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.process.single.processinstance.duration.groupby.none;

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
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.elasticsearch.index.reindex.UpdateByQueryRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.google.common.collect.Lists.newArrayList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.AVERAGE;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.MAX;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.MIN;
import static org.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasName;
import static org.camunda.optimize.test.util.DurationAggregationUtil.getAggregationTypesAsListForProcessParts;
import static org.camunda.optimize.test.util.ProcessReportDataType.PROC_INST_DUR_GROUP_BY_NONE_WITH_PART;
import static org.camunda.optimize.util.BpmnModels.END_LOOP;
import static org.camunda.optimize.util.BpmnModels.START_LOOP;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.script.Script.DEFAULT_SCRIPT_LANG;

public class ProcessInstanceDurationByNoneWithProcessPartReportEvaluationIT extends AbstractProcessDefinitionIT {

  private static final String END_EVENT = "endEvent";
  private static final String START_EVENT = "startEvent";
  private static final String TEST_ACTIVITY = "testActivity";

  @Test
  public void reportEvaluationForOneProcess() {

    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    OffsetDateTime endDate = startDate.plusSeconds(1);
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    engineDatabaseExtension.changeFlowNodeStartDatesForProcessDefinition(
      processInstanceDto.getDefinitionId(),
      startDate
    );
    engineDatabaseExtension.changeFlowNodeEndDatesForProcessDefinition(
      processInstanceDto.getDefinitionId(),
      endDate
    );
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData =
      createReport(
        processInstanceDto.getProcessDefinitionKey(),
        processInstanceDto.getProcessDefinitionVersion(),
        START_EVENT,
        END_EVENT
      );

    AuthorizedProcessReportEvaluationResponseDto<Double> evaluationResponse =
      reportClient.evaluateNumberReport(reportData);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(processInstanceDto.getProcessDefinitionKey());
    assertThat(resultReportDataDto.getDefinitionVersions()).containsExactly(processInstanceDto.getProcessDefinitionVersion());
    assertThat(resultReportDataDto.getView()).isNotNull();
    assertThat(resultReportDataDto.getView().getEntity()).isEqualTo(ProcessViewEntity.PROCESS_INSTANCE);
    assertThat(resultReportDataDto.getView().getFirstProperty()).isEqualTo(ViewProperty.DURATION);
    assertThat(resultReportDataDto.getGroupBy().getType()).isEqualTo(ProcessGroupByType.NONE);
    assertThat(resultReportDataDto.getConfiguration().getProcessPart()).isPresent();

    assertThat(evaluationResponse.getResult().getInstanceCount()).isEqualTo(1L);
    Double calculatedResult = evaluationResponse.getResult().getFirstMeasureData();
    assertThat(calculatedResult).isEqualTo(1000.);
  }

  @Test
  public void reportEvaluationForOneProcessBigActivityDuration() {

    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    // big activity durations in ms (>32 bit) caused the bug described in OPT-2393
    final long activityDurationInSeconds = Integer.valueOf(Integer.MAX_VALUE).longValue();
    OffsetDateTime endDate = startDate.plusSeconds(activityDurationInSeconds);
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    engineDatabaseExtension.changeFlowNodeStartDatesForProcessDefinition(
      processInstanceDto.getDefinitionId(),
      startDate
    );
    engineDatabaseExtension.changeFlowNodeEndDatesForProcessDefinition(
      processInstanceDto.getDefinitionId(),
      endDate
    );
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData =
      createReport(
        processInstanceDto.getProcessDefinitionKey(),
        processInstanceDto.getProcessDefinitionVersion(),
        START_EVENT,
        END_EVENT
      );

    AuthorizedProcessReportEvaluationResponseDto<Double> evaluationResponse =
      reportClient.evaluateNumberReport(reportData);

    // then
    assertThat(evaluationResponse.getResult().getInstanceCount()).isEqualTo(1L);
    Double calculatedResult = evaluationResponse.getResult().getFirstMeasureData();
    assertThat(calculatedResult).isEqualTo(activityDurationInSeconds * 1000.);
  }

  @Test
  public void reportEvaluationById() {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    OffsetDateTime endDate = startDate.plusSeconds(1);
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    engineDatabaseExtension.changeFlowNodeStartDatesForProcessDefinition(
      processInstanceDto.getDefinitionId(),
      startDate
    );
    engineDatabaseExtension.changeFlowNodeEndDatesForProcessDefinition(
      processInstanceDto.getDefinitionId(),
      endDate
    );
    importAllEngineEntitiesFromScratch();
    ProcessReportDataDto reportDataDto =
      createReport(
        processInstanceDto.getProcessDefinitionKey(),
        processInstanceDto.getProcessDefinitionVersion(),
        START_EVENT,
        END_EVENT
      );

    String reportId = createNewReport(reportDataDto);

    // when
    AuthorizedProcessReportEvaluationResponseDto<Double> evaluationResponse =
      reportClient.evaluateNumberReportById(reportId);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(processInstanceDto.getProcessDefinitionKey());
    assertThat(resultReportDataDto.getDefinitionVersions()).containsExactly(processInstanceDto.getProcessDefinitionVersion());
    assertThat(resultReportDataDto.getView()).isNotNull();
    assertThat(resultReportDataDto.getView().getEntity()).isEqualTo(ProcessViewEntity.PROCESS_INSTANCE);
    assertThat(resultReportDataDto.getView().getFirstProperty()).isEqualTo(ViewProperty.DURATION);
    assertThat(resultReportDataDto.getGroupBy().getType()).isEqualTo(ProcessGroupByType.NONE);
    assertThat(resultReportDataDto.getConfiguration().getProcessPart()).isPresent();

    Double calculatedResult = evaluationResponse.getResult().getFirstMeasureData();
    assertThat(calculatedResult).isEqualTo(1000.);
  }

  @Test
  public void evaluateReportForMultipleEventsWithAllAggregationTypes() {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    ProcessInstanceEngineDto processInstanceDto2 =
      engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId());
    ProcessInstanceEngineDto processInstanceDto3 =
      engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId());
    Map<String, OffsetDateTime> startDatesToUpdate = new HashMap<>();
    startDatesToUpdate.put(processInstanceDto.getId(), startDate);
    startDatesToUpdate.put(processInstanceDto2.getId(), startDate);
    startDatesToUpdate.put(processInstanceDto3.getId(), startDate);
    engineDatabaseExtension.changeAllFlowNodeStartDates(startDatesToUpdate);
    Map<String, OffsetDateTime> endDatesToUpdate = new HashMap<>();
    endDatesToUpdate.put(processInstanceDto.getId(), startDate.plusSeconds(1));
    endDatesToUpdate.put(processInstanceDto2.getId(), startDate.plusSeconds(2));
    endDatesToUpdate.put(processInstanceDto3.getId(), startDate.plusSeconds(9));
    engineDatabaseExtension.changeAllFlowNodeEndDates(endDatesToUpdate);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData =
      createReport(
        processInstanceDto.getProcessDefinitionKey(),
        processInstanceDto.getProcessDefinitionVersion(),
        START_EVENT,
        END_EVENT
      );
    reportData.getConfiguration().setAggregationTypes(getAggregationTypesAsListForProcessParts());

    final AuthorizedProcessReportEvaluationResponseDto<Double> evaluationResponse =
      reportClient.evaluateNumberReport(reportData);

    // then
    assertAggregationResults(evaluationResponse);
  }

  @Test
  public void takeCorrectActivityOccurrences() {
    // given
    OffsetDateTime startDate = OffsetDateTime.now().minusHours(1);
    ProcessInstanceEngineDto processInstanceDto = deployAndStartLoopingProcess();
    engineDatabaseExtension.changeFirstFlowNodeInstanceStartDate(START_LOOP, startDate);
    engineDatabaseExtension.changeFirstFlowNodeInstanceEndDate(END_LOOP, startDate.plusSeconds(2));
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData =
      createReport(
        processInstanceDto.getProcessDefinitionKey(),
        processInstanceDto.getProcessDefinitionVersion(),
        START_LOOP,
        END_LOOP
      );

    ReportResultResponseDto<Double> resultDto = reportClient.evaluateNumberReport(reportData).getResult();

    // then
    Double calculatedResult = resultDto.getFirstMeasureData();
    assertThat(calculatedResult).isEqualTo(2000.);
  }

  /**
   * When migrating from Optimize 2.1 to 2.2 then all the activity instances
   * that were imported in 2.1 don't have a start and end date. This test
   * ensures that Optimize can cope with that.
   */
  @Test
  public void activityHasNullDates() {
    // given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    importAllEngineEntitiesFromScratch();
    setActivityStartDatesToNull(processInstanceDto.getProcessDefinitionKey());

    // when
    ProcessReportDataDto reportData =
      createReport(
        processInstanceDto.getProcessDefinitionKey(),
        processInstanceDto.getProcessDefinitionVersion(),
        START_EVENT,
        END_EVENT
      );

    ReportResultResponseDto<Double> resultDto = reportClient.evaluateNumberReport(reportData).getResult();

    // then
    Double calculatedResult = resultDto.getFirstMeasureData();
    assertThat(calculatedResult).isNull();
  }

  private void setActivityStartDatesToNull(final String processDefinitionKey) {
    Script setActivityStartDatesToNull = new Script(
      ScriptType.INLINE,
      DEFAULT_SCRIPT_LANG,
      "for (flowNodeInstance in ctx._source.flowNodeInstances) { flowNodeInstance.startDate = null }",
      Collections.emptyMap()
    );
    UpdateByQueryRequest request = new UpdateByQueryRequest(getProcessInstanceIndexAliasName(processDefinitionKey))
      .setAbortOnVersionConflict(false)
      .setQuery(matchAllQuery())
      .setScript(setActivityStartDatesToNull)
      .setRefresh(true);

    try {
      elasticSearchIntegrationTestExtension.getOptimizeElasticClient().updateByQuery(request);
    } catch (IOException e) {
      throw new OptimizeIntegrationTestException("Could not set activity start dates to null.", e);
    }
  }

  @Test
  public void firstOccurrenceOfEndDateIsBeforeFirstOccurrenceOfStartDate() {
    // given
    OffsetDateTime startDate = OffsetDateTime.now().minusHours(1);
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    engineDatabaseExtension.changeFirstFlowNodeInstanceStartDate(START_EVENT, startDate);
    engineDatabaseExtension.changeFirstFlowNodeInstanceEndDate(END_EVENT, startDate.minusSeconds(2));
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData =
      createReport(
        processInstanceDto.getProcessDefinitionKey(),
        processInstanceDto.getProcessDefinitionVersion(),
        START_EVENT,
        END_EVENT
      );

    ReportResultResponseDto<Double> resultDto = reportClient.evaluateNumberReport(reportData).getResult();

    // then
    Double calculatedResult = resultDto.getFirstMeasureData();
    assertThat(calculatedResult).isNull();
  }

  @Test
  public void unknownStartReturnsZero() {
    // given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    engineDatabaseExtension.changeFlowNodeEndDatesForProcessDefinition(
      processInstanceDto.getDefinitionId(),
      OffsetDateTime.now().plusHours(1)
    );
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData =
      createReport(
        processInstanceDto.getProcessDefinitionKey(),
        processInstanceDto.getProcessDefinitionVersion(),
        "FOoO",
        END_EVENT
      );

    ReportResultResponseDto<Double> resultDto = reportClient.evaluateNumberReport(reportData).getResult();

    // then
    Double calculatedResult = resultDto.getFirstMeasureData();
    assertThat(resultDto.getInstanceCount()).isEqualTo(0L);
    assertThat(calculatedResult).isNull();
  }

  @Test
  public void unknownEndReturnsZero() {
    // given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    engineDatabaseExtension.changeFlowNodeStartDatesForProcessDefinition(
      processInstanceDto.getDefinitionId(),
      OffsetDateTime.now().minusHours(1)
    );
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData =
      createReport(
        processInstanceDto.getProcessDefinitionKey(),
        processInstanceDto.getProcessDefinitionVersion(),
        START_EVENT,
        "FOO"
      );

    ReportResultResponseDto<Double> resultDto = reportClient.evaluateNumberReport(reportData).getResult();

    // then
    Double calculatedResult = resultDto.getFirstMeasureData();
    assertThat(resultDto.getInstanceCount()).isEqualTo(0L);
    assertThat(calculatedResult).isNull();
  }

  @Test
  public void noAvailableProcessInstancesReturnsNull() {
    // when
    ProcessReportDataDto reportData =
      createReport(
        "FOOPROCDEF",
        "1",
        START_EVENT,
        END_EVENT
      );

    ReportResultResponseDto<Double> resultDto = reportClient.evaluateNumberReport(reportData).getResult();

    // then
    Double calculatedResult = resultDto.getFirstMeasureData();
    assertThat(resultDto.getInstanceCount()).isEqualTo(0L);
    assertThat(calculatedResult).isNull();
  }

  @Test
  public void otherProcessDefinitionsDoNoAffectResult() {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();

    String processDefinitionKey = processInstanceDto.getProcessDefinitionKey();
    String processDefinitionVersion = processInstanceDto.getProcessDefinitionVersion();

    engineDatabaseExtension.changeAllFlowNodeStartDates(processInstanceDto.getId(), startDate);
    engineDatabaseExtension.changeAllFlowNodeEndDates(processInstanceDto.getId(), startDate.plusSeconds(1));
    processInstanceDto = engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId());
    engineDatabaseExtension.changeAllFlowNodeStartDates(processInstanceDto.getId(), startDate);
    engineDatabaseExtension.changeAllFlowNodeEndDates(processInstanceDto.getId(), startDate.plusSeconds(9));
    processInstanceDto = engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId());
    engineDatabaseExtension.changeAllFlowNodeStartDates(processInstanceDto.getId(), startDate);
    engineDatabaseExtension.changeAllFlowNodeEndDates(processInstanceDto.getId(), startDate.plusSeconds(2));
    deployAndStartSimpleServiceTaskProcess();
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData =
      createReport(
        processDefinitionKey,
        processDefinitionVersion,
        START_EVENT,
        END_EVENT
      );

    reportData.getConfiguration().setAggregationTypes(getAggregationTypesAsListForProcessParts());
    final AuthorizedProcessReportEvaluationResponseDto<Double> evaluationResponse =
      reportClient.evaluateNumberReport(reportData);

    // then
    assertAggregationResults(evaluationResponse);
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
    ProcessReportDataDto reportData =
      createReport(processKey, ALL_VERSIONS, START_EVENT, END_EVENT);
    reportData.setTenantIds(selectedTenants);
    ReportResultResponseDto<Double> result = reportClient.evaluateNumberReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo((long) selectedTenants.size());
  }

  @Test
  public void filterInReportWorks() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", true);
    OffsetDateTime startDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcessWithVariables(variables);
    engineDatabaseExtension.changeAllFlowNodeStartDates(processInstanceDto.getId(), startDate);
    engineDatabaseExtension.changeAllFlowNodeEndDates(processInstanceDto.getId(), startDate.plusSeconds(1));
    String processDefinitionId = processInstanceDto.getDefinitionId();
    engineIntegrationExtension.startProcessInstance(processDefinitionId);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData =
      createReport(
        processInstanceDto.getProcessDefinitionKey(),
        processInstanceDto.getProcessDefinitionVersion(),
        START_EVENT,
        END_EVENT
      );
    reportData.setFilter(createVariableFilter("true"));
    ReportResultResponseDto<Double> resultDto = reportClient.evaluateNumberReport(reportData).getResult();

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

  private List<ProcessFilterDto<?>> createVariableFilter(String value) {
    return ProcessFilterBuilder
      .filter()
      .variable()
      .booleanType()
      .values(Collections.singletonList(value))
      .name("var")
      .add()
      .buildList();
  }

  private ProcessInstanceEngineDto deployAndStartSimpleServiceTaskProcessWithVariables(Map<String, Object> variables) {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent(START_EVENT)
      .serviceTask(TEST_ACTIVITY)
      .camundaExpression("${true}")
      .endEvent(END_EVENT)
      .done();
    return engineIntegrationExtension.deployAndStartProcessWithVariables(processModel, variables);
  }

  private void assertAggregationResults(AuthorizedProcessReportEvaluationResponseDto<Double> evaluationResponse) {
    final Map<AggregationDto, Double> resultByAggregationType = evaluationResponse.getResult()
      .getMeasures()
      .stream()
      .collect(Collectors.toMap(MeasureResponseDto::getAggregationType, MeasureResponseDto::getData));
    assertThat(resultByAggregationType)
      .hasSize(getAggregationTypesAsListForProcessParts().length)
      .containsEntry(new AggregationDto(AVERAGE), 4000.)
      .containsEntry(new AggregationDto(MIN), 1000.)
      .containsEntry(new AggregationDto(MAX), 9000.);
  }

  private ProcessReportDataDto createReport(String definitionKey, String definitionVersion, String start, String end) {
    return TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(definitionKey)
      .setProcessDefinitionVersion(definitionVersion)
      .setStartFlowNodeId(start)
      .setEndFlowNodeId(end)
      .setReportDataType(PROC_INST_DUR_GROUP_BY_NONE_WITH_PART)
      .build();
  }

}
