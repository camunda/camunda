/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.retrieval;

import com.fasterxml.jackson.core.type.TypeReference;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.configuration.CombinedReportConfigurationDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportNumberResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.duration.AggregationResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.duration.ProcessDurationReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.result.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.rest.report.CombinedReportEvaluationResultDto;
import org.camunda.optimize.dto.optimize.rest.report.EvaluationResultDto;
import org.camunda.optimize.dto.optimize.rest.report.ProcessReportEvaluationResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.RequestOptions;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.test.it.rule.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createCombinedReport;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createCountFlowNodeFrequencyGroupByFlowNode;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createFlowNodeDurationGroupByFlowNodeTableReport;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createPiFrequencyCountGroupedByNone;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createProcessInstanceDurationGroupByNone;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createProcessReportDataViewRawAsTable;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createUserTaskIdleDurationMapGroupByUserTaskReport;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createUserTaskTotalDurationMapGroupByUserTaskReport;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.COMBINED_REPORT_TYPE;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.IsNull.notNullValue;


public class CombinedReportHandlingIT {

  private static final String START_EVENT = "startEvent";
  private static final String END_EVENT = "endEvent";
  private static final String SERVICE_TASK_ID = "aSimpleServiceTask";
  private static final String TEST_REPORT_NAME = "My foo report";

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  @After
  public void cleanUp() {
    LocalDateUtil.reset();
  }

  @Test
  public void reportIsWrittenToElasticsearch() throws IOException {
    // given
    String id = createNewCombinedReport();

    // then
    GetRequest getRequest = new GetRequest(
      getOptimizeIndexAliasForType(COMBINED_REPORT_TYPE),
      COMBINED_REPORT_TYPE,
      id
    );
    GetResponse getResponse = elasticSearchRule.getEsClient().get(getRequest, RequestOptions.DEFAULT);

    assertThat(getResponse.isExists(), is(true));
    CombinedReportDefinitionDto definitionDto = elasticSearchRule.getObjectMapper()
      .readValue(getResponse.getSourceAsString(), CombinedReportDefinitionDto.class);
    assertThat(definitionDto.getData(), notNullValue());
    CombinedReportDataDto data = definitionDto.getData();
    assertThat(data.getConfiguration(), notNullValue());
    assertThat(data.getConfiguration(), equalTo(new CombinedReportConfigurationDto()));
    assertThat(definitionDto.getData().getReportIds(), notNullValue());
  }

  @Test
  public void getSingleAndCombinedReport() {
    // given
    String singleReportId = createNewSingleReport();
    String combinedReportId = createNewCombinedReport();

    // when
    List<ReportDefinitionDto> reports = getAllReports();

    // then
    Set<String> resultSet = reports.stream()
      .map(ReportDefinitionDto::getId)
      .collect(Collectors.toSet());
    assertThat(resultSet.size(), is(2));
    assertThat(resultSet.contains(singleReportId), is(true));
    assertThat(resultSet.contains(combinedReportId), is(true));
  }

  @Test
  public void updateCombinedReport() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    String id = createNewCombinedReport();
    String singleReportId = createNewSingleNumberReport(engineDto);
    CombinedReportDefinitionDto report = new CombinedReportDefinitionDto();
    report.setData(createCombinedReport(singleReportId));
    report.getData().getConfiguration().setxLabel("FooXLabel");
    report.setId("shouldNotBeUpdated");
    report.setLastModifier("shouldNotBeUpdatedManually");
    report.setName("MyReport");
    OffsetDateTime shouldBeIgnoredDate = OffsetDateTime.now().plusHours(1);
    report.setCreated(shouldBeIgnoredDate);
    report.setLastModified(shouldBeIgnoredDate);
    report.setOwner("NewOwner");

    // when
    updateReport(id, report);
    List<ReportDefinitionDto> reports = getAllReports();

    // then
    assertThat(reports.size(), is(2));
    CombinedReportDefinitionDto newReport = (CombinedReportDefinitionDto) reports.stream()
      .filter(r -> r instanceof CombinedReportDefinitionDto).findFirst().get();
    assertThat(newReport.getData().getReportIds().isEmpty(), is(false));
    assertThat(newReport.getData().getReportIds().get(0), is(singleReportId));
    assertThat(newReport.getData().getConfiguration().getxLabel(), is("FooXLabel"));
    assertThat(newReport.getData().getVisualization(), is(ProcessVisualization.NUMBER));
    assertThat(newReport.getId(), is(id));
    assertThat(newReport.getCreated(), is(not(shouldBeIgnoredDate)));
    assertThat(newReport.getLastModified(), is(not(shouldBeIgnoredDate)));
    assertThat(newReport.getName(), is("MyReport"));
    assertThat(newReport.getOwner(), is("NewOwner"));
  }

  @Test
  public void addUncombinableReportThrowsError() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String numberReportId = createNewSingleNumberReport(engineDto);
    String rawReportId = createNewSingleRawReport(engineDto);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    String combinedReportId = createNewCombinedReport();
    CombinedReportDefinitionDto combinedReport = new CombinedReportDefinitionDto();
    combinedReport.setData(createCombinedReport(numberReportId, rawReportId));
    Response response = getUpdateReportResponse(combinedReportId, combinedReport, true);

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void reportEvaluationReturnsMetaData() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String singleReportId = createNewSingleMapReport(engineDto);
    String reportId = createNewCombinedReport();
    CombinedReportDefinitionDto report = new CombinedReportDefinitionDto();
    report.setData(createCombinedReport(singleReportId));
    report.setName("name");
    OffsetDateTime now = OffsetDateTime.now();
    report.setOwner("owner");
    updateReport(reportId, report);

    // when
    CombinedReportEvaluationResultDto<ProcessReportMapResultDto> result = evaluateCombinedReportById(reportId);

    // then
    assertThat(result.getReportDefinition().getId(), is(reportId));
    assertThat(result.getReportDefinition().getName(), is("name"));
    assertThat(result.getReportDefinition().getOwner(), is("owner"));
    assertThat(result.getReportDefinition().getCreated().truncatedTo(ChronoUnit.DAYS), is(now.truncatedTo(ChronoUnit.DAYS)));
    assertThat(result.getReportDefinition().getLastModifier(), is(DEFAULT_USERNAME));
    assertThat(result.getReportDefinition().getLastModified().truncatedTo(ChronoUnit.DAYS), is(now.truncatedTo(ChronoUnit.DAYS)));
    assertThat(result.getResult().getData(), is(notNullValue()));
    assertThat(result.getReportDefinition().getData().getReportIds().size(), is(1));
    assertThat(result.getReportDefinition().getData().getReportIds().get(0), is(singleReportId));
    assertThat(result.getReportDefinition().getData().getConfiguration(), equalTo(new CombinedReportConfigurationDto()));
  }

  @Test
  public void deleteCombinedReport() {
    // given
    String reportId = createNewCombinedReport();
    CombinedReportDefinitionDto report = new CombinedReportDefinitionDto();
    report.setData(createCombinedReport());
    report.setName("name");
    OffsetDateTime now = OffsetDateTime.now();
    report.setOwner("owner");
    updateReport(reportId, report);

    // when
    deleteReport(reportId);
    List<ReportDefinitionDto> reports = getAllReports();

    // then
    assertThat(reports.size(), is(0));
  }

  @Test
  public void canSaveAndEvaluateCombinedReports() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String singleReportId = createNewSingleMapReport(engineDto);
    String singleReportId2 = createNewSingleMapReport(engineDto);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    String reportId = createNewCombinedReport(singleReportId, singleReportId2);
    CombinedReportEvaluationResultDto<ProcessReportMapResultDto> result = evaluateCombinedReportById(reportId);

    // then
    assertThat(result.getReportDefinition().getId(), is(reportId));
    Map<String, ProcessReportEvaluationResultDto<ProcessReportMapResultDto>> resultMap = result.getResult().getData();
    assertThat(resultMap.size(), is(2));
    List<MapResultEntryDto<Long>> flowNodeToCount = resultMap.get(singleReportId).getResult().getData();
    assertThat(flowNodeToCount.size(), is(3));
    List<MapResultEntryDto<Long>> flowNodeToCount2 = resultMap.get(singleReportId2).getResult().getData();
    assertThat(flowNodeToCount2.size(), is(3));
  }

  @Test
  public void canSaveAndEvaluateCombinedReportsWithUserTaskDurationReportsOfDifferentDurationViewProperties() {
    // given
    ProcessInstanceEngineDto engineDto = deployAndStartSimpleUserTaskProcess();
    engineRule.finishAllUserTasks();
    String totalDurationReportId = createNewSingleUserTaskTotalDurationMapReport(engineDto);
    String idleDurationReportId = createNewSingleUserTaskIdleDurationMapReport(engineDto);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    String reportId = createNewCombinedReport(totalDurationReportId, idleDurationReportId);
    CombinedReportEvaluationResultDto<ProcessDurationReportMapResultDto> result = evaluateCombinedReportById(reportId);

    // then
    assertThat(result.getReportDefinition().getId(), is(reportId));
    Map<String, ProcessReportEvaluationResultDto<ProcessDurationReportMapResultDto>> resultMap =
      result.getResult().getData();
    assertThat(resultMap.size(), is(2));
    List<MapResultEntryDto<AggregationResultDto>> userTaskCount1 = resultMap.get(totalDurationReportId).getResult().getData();
    assertThat(userTaskCount1.size(), is(1));
    List<MapResultEntryDto<AggregationResultDto>> userTaskCount2 = resultMap.get(idleDurationReportId).getResult().getData();
    assertThat(userTaskCount2.size(), is(1));
  }

  @Test
  public void canSaveAndEvaluateCombinedReportsWithUserTaskDurationAndProcessDurationReports() {
    // given
    ProcessInstanceEngineDto engineDto = deployAndStartSimpleUserTaskProcess();
    engineRule.finishAllUserTasks();
    String userTaskTotalDurationReportId = createNewSingleUserTaskTotalDurationMapReport(engineDto);
    String flowNodeDurationReportId = createNewSingleDurationMapReport(engineDto);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    String reportId = createNewCombinedReport(userTaskTotalDurationReportId, flowNodeDurationReportId);
    CombinedReportEvaluationResultDto<ProcessDurationReportMapResultDto> result = evaluateCombinedReportById(reportId);

    // then
    assertThat(result.getReportDefinition().getId(), is(reportId));
    Map<String, ProcessReportEvaluationResultDto<ProcessDurationReportMapResultDto>> resultMap =
      result.getResult().getData();
    assertThat(resultMap.size(), is(2));
    List<MapResultEntryDto<AggregationResultDto>> userTaskCount1 = resultMap.get(userTaskTotalDurationReportId).getResult().getData();
    assertThat(userTaskCount1.size(), is(1));
    List<MapResultEntryDto<AggregationResultDto>> userTaskCount2 = resultMap.get(flowNodeDurationReportId).getResult().getData();
    assertThat(userTaskCount2.size(), is(3));
  }

  @Test
  public void reportsThatCantBeEvaluatedAreIgnored() {
    // given
    deploySimpleServiceTaskProcessDefinition();
    String singleReportId = createNewSingleReport();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    String reportId = createNewCombinedReport(singleReportId);
    CombinedReportEvaluationResultDto<ProcessReportMapResultDto> result = evaluateCombinedReportById(reportId);

    // then
    assertThat(result.getReportDefinition().getId(), is(reportId));
    Map<String, ProcessReportEvaluationResultDto<ProcessReportMapResultDto>> resultMap =
      result.getResult().getData();
    assertThat(resultMap.size(), is(0));
  }

  @Test
  public void deletedSingleReportsAreRemovedFromCombinedReportWhenForced() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String singleReportIdToDelete = createNewSingleMapReport(engineDto);
    String remainingSingleReportId = createNewSingleMapReport(engineDto);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    String combinedReportId = createNewCombinedReport(singleReportIdToDelete, remainingSingleReportId);
    deleteReport(singleReportIdToDelete, true);
    List<ReportDefinitionDto> reports = getAllReports();

    // then
    Set<String> resultSet = reports.stream()
      .map(ReportDefinitionDto::getId)
      .collect(Collectors.toSet());
    assertThat(resultSet.size(), is(2));
    assertThat(resultSet.contains(remainingSingleReportId), is(true));
    assertThat(resultSet.contains(combinedReportId), is(true));
    Optional<CombinedReportDefinitionDto> combinedReport = reports.stream()
      .filter(r -> r instanceof CombinedReportDefinitionDto)
      .map(r -> (CombinedReportDefinitionDto) r)
      .findFirst();
    assertThat(combinedReport.isPresent(), is(true));
    CombinedReportDataDto dataDto = combinedReport.get().getData();
    assertThat(dataDto.getReportIds().size(), is(1));
    assertThat(dataDto.getReportIds().get(0), is(remainingSingleReportId));
  }

  @Test
  public void singleReportsAreRemovedFromCombinedReportOnReportUpdateWithVisualizeAsChangedWhenForced() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    ProcessReportDataDto countFlowNodeFrequencyGroupByFlowNode = createCountFlowNodeFrequencyGroupByFlowNode(
      engineDto.getProcessDefinitionKey(),
      engineDto.getProcessDefinitionVersion()
    );
    String singleReportIdToUpdate = createNewSingleMapReport(countFlowNodeFrequencyGroupByFlowNode);
    String remainingSingleReportId = createNewSingleMapReport(engineDto);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    String combinedReportId = createNewCombinedReport(singleReportIdToUpdate, remainingSingleReportId);
    SingleProcessReportDefinitionDto report = new SingleProcessReportDefinitionDto();
    countFlowNodeFrequencyGroupByFlowNode.setVisualization(ProcessVisualization.TABLE);
    report.setData(countFlowNodeFrequencyGroupByFlowNode);
    updateReport(singleReportIdToUpdate, report, true);
    List<ReportDefinitionDto> reports = getAllReports();

    // then
    Set<String> resultSet = reports.stream()
      .map(ReportDefinitionDto::getId)
      .collect(Collectors.toSet());
    assertThat(resultSet.size(), is(3));
    assertThat(resultSet.contains(combinedReportId), is(true));
    Optional<CombinedReportDefinitionDto> combinedReport = reports.stream()
      .filter(r -> r instanceof CombinedReportDefinitionDto)
      .map(r -> (CombinedReportDefinitionDto) r)
      .findFirst();
    assertThat(combinedReport.isPresent(), is(true));
    CombinedReportDataDto dataDto = combinedReport.get().getData();
    assertThat(dataDto.getReportIds().size(), is(1));
    assertThat(dataDto.getReportIds().get(0), is(remainingSingleReportId));
  }

  @Test
  public void singleReportsAreRemovedFromCombinedReportOnReportUpdateWithGroupByChangedWhenForced() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    ProcessReportDataDto countFlowNodeFrequencyGroupByFlowNode = createCountFlowNodeFrequencyGroupByFlowNode(
      engineDto.getProcessDefinitionKey(),
      engineDto.getProcessDefinitionVersion()
    );
    String singleReportIdToUpdate = createNewSingleMapReport(countFlowNodeFrequencyGroupByFlowNode);
    String remainingSingleReportId = createNewSingleMapReport(engineDto);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    String combinedReportId = createNewCombinedReport(singleReportIdToUpdate, remainingSingleReportId);
    SingleProcessReportDefinitionDto report = new SingleProcessReportDefinitionDto();
    countFlowNodeFrequencyGroupByFlowNode.getGroupBy().setType(ProcessGroupByType.START_DATE);
    report.setData(countFlowNodeFrequencyGroupByFlowNode);
    updateReport(singleReportIdToUpdate, report, true);
    List<ReportDefinitionDto> reports = getAllReports();

    // then
    Set<String> resultSet = reports.stream()
      .map(ReportDefinitionDto::getId)
      .collect(Collectors.toSet());
    assertThat(resultSet.size(), is(3));
    assertThat(resultSet.contains(combinedReportId), is(true));
    Optional<CombinedReportDefinitionDto> combinedReport = reports.stream()
      .filter(r -> r instanceof CombinedReportDefinitionDto)
      .map(r -> (CombinedReportDefinitionDto) r)
      .findFirst();
    assertThat(combinedReport.isPresent(), is(true));
    CombinedReportDataDto dataDto = combinedReport.get().getData();
    assertThat(dataDto.getReportIds().size(), is(1));
    assertThat(dataDto.getReportIds().get(0), is(remainingSingleReportId));
  }

  @Test
  public void singleReportsAreRemovedFromCombinedReportOnReportUpdateWithViewChangedWhenForced() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    ProcessReportDataDto countFlowNodeFrequencyGroupByFlowNode = createCountFlowNodeFrequencyGroupByFlowNode(
      engineDto.getProcessDefinitionKey(),
      engineDto.getProcessDefinitionVersion()
    );
    String singleReportIdToUpdate = createNewSingleMapReport(countFlowNodeFrequencyGroupByFlowNode);
    String remainingSingleReportId = createNewSingleMapReport(engineDto);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    String combinedReportId = createNewCombinedReport(singleReportIdToUpdate, remainingSingleReportId);
    SingleProcessReportDefinitionDto report = new SingleProcessReportDefinitionDto();
    countFlowNodeFrequencyGroupByFlowNode.getView().setEntity(ProcessViewEntity.PROCESS_INSTANCE);
    report.setData(countFlowNodeFrequencyGroupByFlowNode);
    updateReport(singleReportIdToUpdate, report, true);
    List<ReportDefinitionDto> reports = getAllReports();

    // then
    Set<String> resultSet = reports.stream()
      .map(ReportDefinitionDto::getId)
      .collect(Collectors.toSet());
    assertThat(resultSet.size(), is(3));
    assertThat(resultSet.contains(combinedReportId), is(true));
    Optional<CombinedReportDefinitionDto> combinedReport = reports.stream()
      .filter(r -> r instanceof CombinedReportDefinitionDto)
      .map(r -> (CombinedReportDefinitionDto) r)
      .findFirst();
    assertThat(combinedReport.isPresent(), is(true));
    CombinedReportDataDto dataDto = combinedReport.get().getData();
    assertThat(dataDto.getReportIds().size(), is(1));
    assertThat(dataDto.getReportIds().get(0), is(remainingSingleReportId));
  }

  @Test
  public void canEvaluateUnsavedCombinedReport() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String singleReportId = createNewSingleMapReport(engineDto);
    String singleReportId2 = createNewSingleMapReport(engineDto);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    CombinedReportEvaluationResultDto<ProcessReportMapResultDto> result =
      evaluateUnsavedCombined(createCombinedReport(singleReportId, singleReportId2));

    // then
    Map<String, ProcessReportEvaluationResultDto<ProcessReportMapResultDto>> resultMap =
      result.getResult().getData();
    assertThat(resultMap.size(), is(2));
    List<MapResultEntryDto<Long>> flowNodeToCount = resultMap.get(singleReportId).getResult().getData();
    assertThat(flowNodeToCount.size(), is(3));
    List<MapResultEntryDto<Long>> flowNodeToCount2 = resultMap.get(singleReportId2).getResult().getData();
    assertThat(flowNodeToCount2.size(), is(3));
  }

  @Test
  public void evaluationResultContainsSingleResultMetaData() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String singleReportId = createNewSingleMapReport(engineDto);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    CombinedReportEvaluationResultDto<ProcessReportMapResultDto> result =
      evaluateUnsavedCombined(createCombinedReport(singleReportId));

    // then
    Map<String, ProcessReportEvaluationResultDto<ProcessReportMapResultDto>> resultMap =
      result.getResult().getData();
    assertThat(resultMap.size(), is(1));
    EvaluationResultDto<ProcessReportMapResultDto, SingleProcessReportDefinitionDto> mapResult =
      resultMap.get(singleReportId);
    assertThat(mapResult.getReportDefinition().getName(), is(TEST_REPORT_NAME));
  }

  @Test
  public void canEvaluateUnsavedCombinedReportWithSingleNumberReports() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String singleReportId1 = createNewSingleNumberReport(engineDto);
    String singleReportId2 = createNewSingleNumberReport(engineDto);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    CombinedReportEvaluationResultDto<ProcessReportNumberResultDto> result = evaluateUnsavedCombined(
      createCombinedReport(singleReportId1, singleReportId2)
    );

    // then
    Map<String, ProcessReportEvaluationResultDto<ProcessReportNumberResultDto>> resultMap =
      result.getResult().getData();
    assertThat(resultMap.size(), is(2));
    assertThat(resultMap.keySet(), contains(singleReportId1, singleReportId2));
  }

  @Test
  public void canEvaluateUnsavedCombinedReportWithSingleMapReports() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String singleReportId1 = createNewSingleMapReport(engineDto);
    String singleReportId2 = createNewSingleMapReport(engineDto);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    CombinedReportEvaluationResultDto<ProcessReportMapResultDto> result = evaluateUnsavedCombined(
      createCombinedReport(singleReportId1, singleReportId2)
    );

    // then
    Map<String, ProcessReportEvaluationResultDto<ProcessReportMapResultDto>> resultMap =
      result.getResult().getData();
    assertThat(resultMap.size(), is(2));
    assertThat(resultMap.keySet(), contains(singleReportId1, singleReportId2));
  }

  @Test
  public void canEvaluateUnsavedCombinedReportWithProcessDurationNumberReports() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String singleReportId = createNewSingleDurationNumberReport(engineDto);
    String singleReportId2 = createNewSingleDurationNumberReport(engineDto);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    CombinedReportEvaluationResultDto<ProcessReportNumberResultDto> result = evaluateUnsavedCombined(
      createCombinedReport(singleReportId, singleReportId2)
    );

    // then
    Map<String, ProcessReportEvaluationResultDto<ProcessReportNumberResultDto>> resultMap =
      result.getResult().getData();
    assertThat(resultMap.size(), is(2));
    assertThat(resultMap.keySet(), contains(singleReportId, singleReportId2));
  }

  @Test
  public void canEvaluateUnsavedCombinedReportWithProcessDurationMapReports() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String singleReportId = createNewSingleDurationMapReport(engineDto);
    String singleReportId2 = createNewSingleDurationMapReport(engineDto);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    CombinedReportEvaluationResultDto<ProcessReportNumberResultDto> result = evaluateUnsavedCombined(
      createCombinedReport(singleReportId, singleReportId2)
    );

    // then
    Map<String, ProcessReportEvaluationResultDto<ProcessReportNumberResultDto>> resultMap =
      result.getResult().getData();
    assertThat(resultMap.size(), is(2));
    assertThat(resultMap.keySet(), contains(singleReportId, singleReportId2));
  }

  @Test
  public void canEvaluateUnsavedCombinedReportWithProcessUserTaskTotalDurationMapReports() {
    // given
    ProcessInstanceEngineDto engineDto = deployAndStartSimpleUserTaskProcess();
    engineRule.finishAllUserTasks();
    String totalDurationReportId = createNewSingleUserTaskTotalDurationMapReport(engineDto);
    String totalDurationReportId2 = createNewSingleUserTaskTotalDurationMapReport(engineDto);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    CombinedReportEvaluationResultDto<ProcessReportNumberResultDto> result = evaluateUnsavedCombined(
      createCombinedReport(totalDurationReportId, totalDurationReportId2)
    );

    // then
    Map<String, ProcessReportEvaluationResultDto<ProcessReportNumberResultDto>> resultMap =
      result.getResult().getData();
    assertThat(resultMap.size(), is(2));
    assertThat(resultMap.keySet(), contains(totalDurationReportId, totalDurationReportId2));
  }

  @Test
  public void canEvaluateUnsavedCombinedReportWithProcessUserTaskTotalDurationAndUserTaskIdleDurationMapReports() {
    // given
    ProcessInstanceEngineDto engineDto = deployAndStartSimpleUserTaskProcess();
    engineRule.finishAllUserTasks();
    String totalDurationReportId = createNewSingleUserTaskTotalDurationMapReport(engineDto);
    String idleDurationReportId = createNewSingleUserTaskIdleDurationMapReport(engineDto);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    CombinedReportEvaluationResultDto<ProcessReportNumberResultDto> result = evaluateUnsavedCombined(
      createCombinedReport(totalDurationReportId, idleDurationReportId)
    );

    // then
    Map<String, ProcessReportEvaluationResultDto<ProcessReportNumberResultDto>> resultMap =
      result.getResult().getData();
    assertThat(resultMap.size(), is(2));
    assertThat(resultMap.keySet(), contains(totalDurationReportId, idleDurationReportId));
  }

  @Test
  public void canEvaluateUnsavedCombinedReportWithProcessDurationMapReportAndUserTaskTotalDurationMapReport() {
    // given
    ProcessInstanceEngineDto engineDto = deployAndStartSimpleUserTaskProcess();
    engineRule.finishAllUserTasks();
    String totalDurationReportId = createNewSingleDurationMapReport(engineDto);
    String idleDurationReportId = createNewSingleUserTaskIdleDurationMapReport(engineDto);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    CombinedReportEvaluationResultDto<ProcessReportNumberResultDto> result = evaluateUnsavedCombined(
      createCombinedReport(totalDurationReportId, idleDurationReportId)
    );

    // then
    Map<String, ProcessReportEvaluationResultDto<ProcessReportNumberResultDto>> resultMap =
      result.getResult().getData();
    assertThat(resultMap.size(), is(2));
    assertThat(resultMap.keySet(), contains(totalDurationReportId, idleDurationReportId));
  }

  @Test
  public void canEvaluateUnsavedCombinedReportWithSingleNumberAndMapReport_firstWins() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String singleReportId = createNewSingleNumberReport(engineDto);
    String singleReportId2 = createNewSingleMapReport(engineDto);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    CombinedReportEvaluationResultDto<ProcessReportNumberResultDto> result = evaluateUnsavedCombined(
      createCombinedReport(singleReportId, singleReportId2)
    );

    // then
    Map<String, ProcessReportEvaluationResultDto<ProcessReportNumberResultDto>> resultMap =
      result.getResult().getData();
    assertThat(resultMap.size(), is(1));
    assertThat(resultMap.keySet(), contains(singleReportId));
  }

  @Test
  public void canEvaluateUnsavedCombinedReportWithRawReport() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String singleReportId = createNewSingleRawReport(engineDto);
    String singleReportId2 = createNewSingleMapReport(engineDto);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    CombinedReportEvaluationResultDto<ProcessReportMapResultDto> result =
      evaluateUnsavedCombined(createCombinedReport(singleReportId, singleReportId2));

    // then
    Map<String, ProcessReportEvaluationResultDto<ProcessReportMapResultDto>> resultMap =
      result.getResult().getData();
    assertThat(resultMap.size(), is(1));
    assertThat(resultMap.containsKey(singleReportId2), is(true));
  }

  @Test
  public void cantEvaluateCombinedReportWithCombinedReport() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String combinedReportId = createNewCombinedReport();
    String singleReportId2 = createNewSingleMapReport(engineDto);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    Response response =
      evaluateUnsavedCombinedReportAndReturnResponse(createCombinedReport(combinedReportId, singleReportId2));

    // then
    assertThat(response.getStatus(), is(404));
  }

  private String createNewSingleMapReport(ProcessInstanceEngineDto engineDto) {
    ProcessReportDataDto countFlowNodeFrequencyGroupByFlowNode =
      createCountFlowNodeFrequencyGroupByFlowNode(
        engineDto.getProcessDefinitionKey(),
        engineDto.getProcessDefinitionVersion()
      );
    return createNewSingleMapReport(countFlowNodeFrequencyGroupByFlowNode);
  }

  private String createNewSingleDurationNumberReport(ProcessInstanceEngineDto engineDto) {
    ProcessReportDataDto durationReportData =
      createProcessInstanceDurationGroupByNone(
        engineDto.getProcessDefinitionKey(),
        engineDto.getProcessDefinitionVersion()
      );
    return createNewSingleNumberReport(durationReportData);
  }

  private String createNewSingleDurationMapReport(ProcessInstanceEngineDto engineDto) {
    ProcessReportDataDto durationMapReportData =
      createFlowNodeDurationGroupByFlowNodeTableReport(
        engineDto.getProcessDefinitionKey(),
        engineDto.getProcessDefinitionVersion()
      );
    return createNewSingleMapReport(durationMapReportData);
  }

  private String createNewSingleUserTaskTotalDurationMapReport(ProcessInstanceEngineDto engineDto) {
    ProcessReportDataDto durationMapReportData =
      createUserTaskTotalDurationMapGroupByUserTaskReport(
        engineDto.getProcessDefinitionKey(),
        engineDto.getProcessDefinitionVersion()
      );
    return createNewSingleMapReport(durationMapReportData);
  }

  private String createNewSingleUserTaskIdleDurationMapReport(ProcessInstanceEngineDto engineDto) {
    ProcessReportDataDto durationMapReportData =
      createUserTaskIdleDurationMapGroupByUserTaskReport(
        engineDto.getProcessDefinitionKey(),
        engineDto.getProcessDefinitionVersion()
      );
    return createNewSingleMapReport(durationMapReportData);
  }

  private String createNewSingleMapReport(ProcessReportDataDto data) {
    String singleReportId = createNewSingleReport();
    SingleProcessReportDefinitionDto definitionDto = new SingleProcessReportDefinitionDto();
    definitionDto.setName(TEST_REPORT_NAME);
    definitionDto.setData(data);
    updateReport(singleReportId, definitionDto);
    return singleReportId;
  }

  private String createNewSingleNumberReport(ProcessInstanceEngineDto engineDto) {
    ProcessReportDataDto countFlowNodeFrequencyGroupByFlowNode =
      createPiFrequencyCountGroupedByNone(engineDto.getProcessDefinitionKey(), engineDto.getProcessDefinitionVersion());
    return createNewSingleNumberReport(countFlowNodeFrequencyGroupByFlowNode);
  }

  private String createNewSingleNumberReport(ProcessReportDataDto data) {
    String singleReportId = createNewSingleReport();
    SingleProcessReportDefinitionDto definitionDto = new SingleProcessReportDefinitionDto();
    definitionDto.setData(data);
    updateReport(singleReportId, definitionDto);
    return singleReportId;
  }


  private String createNewSingleRawReport(ProcessInstanceEngineDto engineDto) {
    String singleReportId = createNewSingleReport();
    ProcessReportDataDto countFlowNodeFrequencyGroupByFlowNode =
      createProcessReportDataViewRawAsTable(
        engineDto.getProcessDefinitionKey(),
        engineDto.getProcessDefinitionVersion()
      );
    SingleProcessReportDefinitionDto definitionDto = new SingleProcessReportDefinitionDto();
    definitionDto.setData(countFlowNodeFrequencyGroupByFlowNode);
    updateReport(singleReportId, definitionDto);
    return singleReportId;
  }

  private String createNewCombinedReport(CombinedReportDefinitionDto report) {
    String reportId = createNewCombinedReport();
    updateReport(reportId, report);
    return reportId;
  }

  private String createNewCombinedReport(String... singleReportIds) {
    CombinedReportDefinitionDto report = new CombinedReportDefinitionDto();
    report.setData(createCombinedReport(singleReportIds));
    return createNewCombinedReport(report);
  }

  private ProcessInstanceEngineDto deploySimpleServiceTaskProcessDefinition() {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("aProcess")
      .startEvent(START_EVENT)
      .serviceTask(SERVICE_TASK_ID)
      .camundaExpression("${true}")
      .endEvent(END_EVENT)
      .done();
    return engineRule.deployAndStartProcess(modelInstance);
  }

  private ProcessInstanceEngineDto deployAndStartSimpleUserTaskProcess() {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .startEvent("startEvent")
      .userTask("userTask")
      .endEvent()
      .done();
    return engineRule.deployAndStartProcess(processModel);
  }

  private void deleteReport(String reportId) {
    deleteReport(reportId, null);
  }

  private void deleteReport(String reportId, Boolean force) {
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildDeleteReportRequest(reportId, force)
      .execute();

    // then the status code is okay
    assertThat(response.getStatus(), is(204));
  }

  private String createNewCombinedReport() {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateCombinedReportRequest()
      .execute(IdDto.class, 200)
      .getId();
  }

  private String createNewSingleReport() {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest()
      .execute(IdDto.class, 200)
      .getId();
  }

  private void updateReport(String id, ReportDefinitionDto updatedReport) {
    updateReport(id, updatedReport, null);
  }

  private void updateReport(String id, ReportDefinitionDto updatedReport, Boolean force) {
    Response response = getUpdateReportResponse(id, updatedReport, force);
    assertThat(response.getStatus(), is(204));
  }

  private Response getUpdateReportResponse(String id, ReportDefinitionDto updatedReport, Boolean force) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildUpdateReportRequest(id, updatedReport, force)
      .execute();
  }

  private <T extends ProcessReportResultDto> CombinedReportEvaluationResultDto<T> evaluateCombinedReportById(String reportId) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateSavedReportRequest(reportId)
      // @formatter:off
      .execute(new TypeReference<CombinedReportEvaluationResultDto<T>>() {});
      // @formatter:on
  }

  private <T extends ProcessReportResultDto> CombinedReportEvaluationResultDto<T> evaluateUnsavedCombined(CombinedReportDataDto reportDataDto) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateCombinedUnsavedReportRequest(reportDataDto)
      // @formatter:off
      .execute(new TypeReference<CombinedReportEvaluationResultDto<T>>() {});
      // @formatter:on
  }

  private Response evaluateUnsavedCombinedReportAndReturnResponse(CombinedReportDataDto reportDataDto) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateCombinedUnsavedReportRequest(reportDataDto)
      .execute();
  }

  private List<ReportDefinitionDto> getAllReports() {
    return getAllReportsWithQueryParam(new HashMap<>());
  }

  private List<ReportDefinitionDto> getAllReportsWithQueryParam(Map<String, Object> queryParams) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .addQueryParams(queryParams)
      .buildGetAllReportsRequest()
      .executeAndReturnList(ReportDefinitionDto.class, 200);
  }
}
