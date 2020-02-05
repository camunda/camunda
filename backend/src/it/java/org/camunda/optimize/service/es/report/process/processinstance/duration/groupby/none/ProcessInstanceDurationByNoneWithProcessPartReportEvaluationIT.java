/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.processinstance.duration.groupby.none;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.result.NumberResultDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResultDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.reindex.UpdateByQueryRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.google.common.collect.Lists.newArrayList;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.AVERAGE;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.MAX;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.MEDIAN;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.MIN;
import static org.camunda.optimize.test.util.ProcessReportDataType.PROC_INST_DUR_GROUP_BY_NONE_WITH_PART;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.script.Script.DEFAULT_SCRIPT_LANG;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.IsNull.notNullValue;

public class ProcessInstanceDurationByNoneWithProcessPartReportEvaluationIT extends AbstractProcessDefinitionIT {

  private static final String END_EVENT = "endEvent";
  private static final String START_EVENT = "startEvent";
  private static final String START_LOOP = "mergeExclusiveGateway";
  private static final String END_LOOP = "splittingGateway";
  private static final String TEST_ACTIVITY = "testActivity";

  private final List<AggregationType> aggregationTypes = Arrays.asList(AggregationType.values());

  @Test
  public void reportEvaluationForOneProcess() throws Exception {

    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    OffsetDateTime endDate = startDate.plusSeconds(1);
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    engineDatabaseExtension.changeActivityInstanceStartDateForProcessDefinition(
      processInstanceDto.getDefinitionId(),
      startDate
    );
    engineDatabaseExtension.changeActivityInstanceEndDateForProcessDefinition(processInstanceDto.getDefinitionId(), endDate);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData =
      createReport(
        processInstanceDto.getProcessDefinitionKey(),
        processInstanceDto.getProcessDefinitionVersion(),
        START_EVENT,
        END_EVENT
      );

    AuthorizedProcessReportEvaluationResultDto<NumberResultDto> evaluationResponse =
      evaluateNumberReport(reportData);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getDefinitionVersions(), contains(processInstanceDto.getProcessDefinitionVersion()));
    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getEntity(), is(ProcessViewEntity.PROCESS_INSTANCE));
    assertThat(resultReportDataDto.getView().getProperty(), is(ProcessViewProperty.DURATION));
    assertThat(resultReportDataDto.getGroupBy().getType(), is(ProcessGroupByType.NONE));
    assertThat(resultReportDataDto.getConfiguration().getProcessPart(), not(Optional.empty()));

    assertThat(evaluationResponse.getResult().getInstanceCount(), is(1L));
    long calculatedResult = evaluationResponse.getResult().getData();
    assertThat(calculatedResult, is(1000L));
  }

  @Test
  public void reportEvaluationForOneProcessBigActivityDuration() throws Exception {

    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    // big activity durations in ms (>32 bit) caused the bug described in OPT-2393
    final long activityDurationInSeconds = Integer.valueOf(Integer.MAX_VALUE).longValue();
    OffsetDateTime endDate = startDate.plusSeconds(activityDurationInSeconds);
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    engineDatabaseExtension.changeActivityInstanceStartDateForProcessDefinition(
      processInstanceDto.getDefinitionId(),
      startDate
    );
    engineDatabaseExtension.changeActivityInstanceEndDateForProcessDefinition(processInstanceDto.getDefinitionId(), endDate);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData =
      createReport(
        processInstanceDto.getProcessDefinitionKey(),
        processInstanceDto.getProcessDefinitionVersion(),
        START_EVENT,
        END_EVENT
      );

    AuthorizedProcessReportEvaluationResultDto<NumberResultDto> evaluationResponse =
      evaluateNumberReport(reportData);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getDefinitionVersions(), contains(processInstanceDto.getProcessDefinitionVersion()));
    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getEntity(), is(ProcessViewEntity.PROCESS_INSTANCE));
    assertThat(resultReportDataDto.getView().getProperty(), is(ProcessViewProperty.DURATION));
    assertThat(resultReportDataDto.getGroupBy().getType(), is(ProcessGroupByType.NONE));
    assertThat(resultReportDataDto.getConfiguration().getProcessPart(), not(Optional.empty()));

    assertThat(evaluationResponse.getResult().getInstanceCount(), is(1L));
    long calculatedResult = evaluationResponse.getResult().getData();
    assertThat(calculatedResult, is(activityDurationInSeconds * 1000L));
  }

  @Test
  public void reportEvaluationById() throws Exception {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    OffsetDateTime endDate = startDate.plusSeconds(1);
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    engineDatabaseExtension.changeActivityInstanceStartDateForProcessDefinition(
      processInstanceDto.getDefinitionId(),
      startDate
    );
    engineDatabaseExtension.changeActivityInstanceEndDateForProcessDefinition(processInstanceDto.getDefinitionId(), endDate);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    ProcessReportDataDto reportDataDto =
      createReport(
        processInstanceDto.getProcessDefinitionKey(),
        processInstanceDto.getProcessDefinitionVersion(),
        START_EVENT,
        END_EVENT
      );

    String reportId = createNewReport(reportDataDto);

    // when
    AuthorizedProcessReportEvaluationResultDto<NumberResultDto> evaluationResponse =
      evaluateNumberReportById(reportId);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getDefinitionVersions(), contains(processInstanceDto.getProcessDefinitionVersion()));
    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getEntity(), is(ProcessViewEntity.PROCESS_INSTANCE));
    assertThat(resultReportDataDto.getView().getProperty(), is(ProcessViewProperty.DURATION));
    assertThat(resultReportDataDto.getGroupBy().getType(), is(ProcessGroupByType.NONE));
    assertThat(resultReportDataDto.getConfiguration().getProcessPart(), not(Optional.empty()));

    long calculatedResult = evaluationResponse.getResult().getData();
    assertThat(calculatedResult, is(1000L));
  }

  @Test
  public void evaluateReportForMultipleEventsWithAllAggregationTypes() throws Exception {
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
    engineDatabaseExtension.updateActivityInstanceStartDates(startDatesToUpdate);
    Map<String, OffsetDateTime> endDatesToUpdate = new HashMap<>();
    endDatesToUpdate.put(processInstanceDto.getId(), startDate.plusSeconds(1));
    endDatesToUpdate.put(processInstanceDto2.getId(), startDate.plusSeconds(2));
    endDatesToUpdate.put(processInstanceDto3.getId(), startDate.plusSeconds(9));
    engineDatabaseExtension.updateActivityInstanceEndDates(endDatesToUpdate);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData =
      createReport(
        processInstanceDto.getProcessDefinitionKey(),
        processInstanceDto.getProcessDefinitionVersion(),
        START_EVENT,
        END_EVENT
      );

    final Map<AggregationType, AuthorizedProcessReportEvaluationResultDto<NumberResultDto>> results =
      evaluateMapReportForAllAggTypes(reportData);

    // then
    assertAggregationResults(results);
  }

  @Test
  public void takeCorrectActivityOccurrences() throws Exception {
    // given
    OffsetDateTime startDate = OffsetDateTime.now().minusHours(1);
    ProcessInstanceEngineDto processInstanceDto = deployAndStartLoopingProcess();
    engineDatabaseExtension.changeFirstActivityInstanceStartDate(START_LOOP, startDate);
    engineDatabaseExtension.changeFirstActivityInstanceEndDate(END_LOOP, startDate.plusSeconds(2));
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData =
      createReport(
        processInstanceDto.getProcessDefinitionKey(),
        processInstanceDto.getProcessDefinitionVersion(),
        START_LOOP,
        END_LOOP
      );

    NumberResultDto resultDto = evaluateNumberReport(reportData).getResult();

    // then
    long calculatedResult = resultDto.getData();
    assertThat(calculatedResult, is(2000L));
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
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    setActivityStartDatesToNull();

    // when
    ProcessReportDataDto reportData =
      createReport(
        processInstanceDto.getProcessDefinitionKey(),
        processInstanceDto.getProcessDefinitionVersion(),
        START_EVENT,
        END_EVENT
      );

    NumberResultDto resultDto = evaluateNumberReport(reportData).getResult();

    // then
    long calculatedResult = resultDto.getData();
    assertThat(calculatedResult, is(0L));
  }

  private void setActivityStartDatesToNull() {
    Script setActivityStartDatesToNull = new Script(
      ScriptType.INLINE,
      DEFAULT_SCRIPT_LANG,
      "for (event in ctx._source.events) { event.startDate = null }",
      Collections.emptyMap()
    );
    UpdateByQueryRequest request = new UpdateByQueryRequest(PROCESS_INSTANCE_INDEX_NAME)
      .setAbortOnVersionConflict(false)
      .setQuery(matchAllQuery())
      .setScript(setActivityStartDatesToNull)
      .setRefresh(true);

    try {
      elasticSearchIntegrationTestExtension.getOptimizeElasticClient().updateByQuery(request, RequestOptions.DEFAULT);
    } catch (IOException e) {
      throw new OptimizeIntegrationTestException("Could not set activity start dates to null.", e);
    }
  }

  @Test
  public void firstOccurrenceOfEndDateIsBeforeFirstOccurrenceOfStartDate() throws
                                                                           Exception {
    // given
    OffsetDateTime startDate = OffsetDateTime.now().minusHours(1);
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    engineDatabaseExtension.changeFirstActivityInstanceStartDate(START_EVENT, startDate);
    engineDatabaseExtension.changeFirstActivityInstanceEndDate(END_EVENT, startDate.minusSeconds(2));
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData =
      createReport(
        processInstanceDto.getProcessDefinitionKey(),
        processInstanceDto.getProcessDefinitionVersion(),
        START_EVENT,
        END_EVENT
      );

    NumberResultDto resultDto = evaluateNumberReport(reportData).getResult();

    // then
    long calculatedResult = resultDto.getData();
    assertThat(calculatedResult, is(0L));
  }

  @Test
  public void unknownStartReturnsZero() throws SQLException {
    // given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    engineDatabaseExtension.changeActivityInstanceEndDateForProcessDefinition(
      processInstanceDto.getDefinitionId(),
      OffsetDateTime.now().plusHours(1)
    );
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData =
      createReport(
        processInstanceDto.getProcessDefinitionKey(),
        processInstanceDto.getProcessDefinitionVersion(),
        "FOoO",
        END_EVENT
      );

    NumberResultDto resultDto = evaluateNumberReport(reportData).getResult();

    // then
    long calculatedResult = resultDto.getData();
    assertThat(calculatedResult, is(0L));
  }

  @Test
  public void unknownEndReturnsZero() throws SQLException {
    // given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    engineDatabaseExtension.changeActivityInstanceStartDateForProcessDefinition(
      processInstanceDto.getDefinitionId(),
      OffsetDateTime.now().minusHours(1)
    );
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData =
      createReport(
        processInstanceDto.getProcessDefinitionKey(),
        processInstanceDto.getProcessDefinitionVersion(),
        START_EVENT,
        "FOO"
      );

    NumberResultDto resultDto = evaluateNumberReport(reportData).getResult();

    // then
    long calculatedResult = resultDto.getData();
    assertThat(calculatedResult, is(0L));
  }

  @Test
  public void noAvailableProcessInstancesReturnsZero() {
    // when
    ProcessReportDataDto reportData =
      createReport(
        "FOOPROCDEF",
        "1",
        START_EVENT,
        END_EVENT
      );

    NumberResultDto resultDto = evaluateNumberReport(reportData).getResult();

    // then
    long calculatedResult = resultDto.getData();
    assertThat(calculatedResult, is(0L));
  }

  @Test
  public void otherProcessDefinitionsDoNoAffectResult() throws Exception {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();

    String processDefinitionKey = processInstanceDto.getProcessDefinitionKey();
    String processDefinitionVersion = processInstanceDto.getProcessDefinitionVersion();

    engineDatabaseExtension.changeActivityInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseExtension.changeActivityInstanceEndDate(processInstanceDto.getId(), startDate.plusSeconds(1));
    processInstanceDto = engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId());
    engineDatabaseExtension.changeActivityInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseExtension.changeActivityInstanceEndDate(processInstanceDto.getId(), startDate.plusSeconds(9));
    processInstanceDto = engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId());
    engineDatabaseExtension.changeActivityInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseExtension.changeActivityInstanceEndDate(processInstanceDto.getId(), startDate.plusSeconds(2));
    deployAndStartSimpleServiceTaskProcess();
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData =
      createReport(
        processDefinitionKey,
        processDefinitionVersion,
        START_EVENT,
        END_EVENT
      );

    final Map<AggregationType, AuthorizedProcessReportEvaluationResultDto<NumberResultDto>> results =
      evaluateMapReportForAllAggTypes(reportData);

    // then
    assertAggregationResults(results);
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

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData =
      createReport(processKey, ALL_VERSIONS, START_EVENT, END_EVENT);
    reportData.setTenantIds(selectedTenants);
    NumberResultDto result = evaluateNumberReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount(), is((long) selectedTenants.size()));
  }

  @Test
  public void filterInReportWorks() throws Exception {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", true);
    OffsetDateTime startDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcessWithVariables(variables);
    engineDatabaseExtension.changeActivityInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseExtension.changeActivityInstanceEndDate(processInstanceDto.getId(), startDate.plusSeconds(1));
    String processDefinitionId = processInstanceDto.getDefinitionId();
    engineIntegrationExtension.startProcessInstance(processDefinitionId);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData =
      createReport(
        processInstanceDto.getProcessDefinitionKey(),
        processInstanceDto.getProcessDefinitionVersion(),
        START_EVENT,
        END_EVENT
      );
    reportData.setFilter(createVariableFilter("true"));
    NumberResultDto resultDto = evaluateNumberReport(reportData).getResult();

    // then
    long calculatedResult = resultDto.getData();
    assertThat(calculatedResult, is(1000L));

    // when
    reportData.setFilter(createVariableFilter("false"));
    resultDto = evaluateNumberReport(reportData).getResult();

    // then
    calculatedResult = resultDto.getData();
    assertThat(calculatedResult, is(0L));
  }

  private List<ProcessFilterDto> createVariableFilter(String value) {
    return ProcessFilterBuilder
      .filter()
      .variable()
      .booleanType()
      .values(Collections.singletonList(value))
      .name("var")
      .add()
      .buildList();
  }

  private ProcessInstanceEngineDto deployAndStartLoopingProcess() {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess()
      .startEvent("startEvent")
      .exclusiveGateway(START_LOOP)
      .serviceTask()
      .camundaExpression("${true}")
      .exclusiveGateway(END_LOOP)
      .condition("Take another round", "${!anotherRound}")
      .endEvent("endEvent")
      .moveToLastGateway()
      .condition("End process", "${anotherRound}")
      .serviceTask("serviceTask")
      .camundaExpression("${true}")
      .camundaInputParameter("anotherRound", "${anotherRound}")
      .camundaOutputParameter("anotherRound", "${!anotherRound}")
      .scriptTask("scriptTask")
      .scriptFormat("groovy")
      .scriptText("sleep(10)")
      .connectTo("mergeExclusiveGateway")
      .done();
    Map<String, Object> variables = new HashMap<>();
    variables.put("anotherRound", true);
    return engineIntegrationExtension.deployAndStartProcessWithVariables(modelInstance, variables);
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

  private Map<AggregationType, AuthorizedProcessReportEvaluationResultDto<NumberResultDto>> evaluateMapReportForAllAggTypes(final ProcessReportDataDto reportData) {

    Map<AggregationType, AuthorizedProcessReportEvaluationResultDto<NumberResultDto>> resultsMap =
      new HashMap<>();
    aggregationTypes.forEach((AggregationType aggType) -> {
      reportData.getConfiguration().setAggregationType(aggType);
      AuthorizedProcessReportEvaluationResultDto<NumberResultDto> evaluationResponse =
        evaluateNumberReport(reportData);
      resultsMap.put(aggType, evaluationResponse);
    });
    return resultsMap;
  }

  private void assertAggregationResults(
    Map<AggregationType, AuthorizedProcessReportEvaluationResultDto<NumberResultDto>> results) {
    assertThat(results.get(AVERAGE).getResult().getData(), is(notNullValue()));
    assertThat(results.get(AVERAGE).getResult().getData(), is(4000L));
    assertThat(results.get(MIN).getResult().getData(), is(notNullValue()));
    assertThat(results.get(MIN).getResult().getData(), is(1000L));
    assertThat(results.get(MAX).getResult().getData(), is(notNullValue()));
    assertThat(results.get(MAX).getResult().getData(), is(9000L));
    assertThat(results.get(MEDIAN).getResult().getData(), is(notNullValue()));
    assertThat(results.get(MEDIAN).getResult().getData(), is(2000L));
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
