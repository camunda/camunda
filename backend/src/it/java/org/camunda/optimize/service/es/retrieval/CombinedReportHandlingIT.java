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
import org.camunda.optimize.dto.optimize.query.report.SingleReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportItemDto;
import org.camunda.optimize.dto.optimize.query.report.combined.configuration.CombinedReportConfigurationDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.result.NumberResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedCombinedReportEvaluationResultDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedEvaluationResultDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.test.engine.AuthorizationClient;
import org.camunda.optimize.test.it.extension.ElasticSearchIntegrationTestExtensionRule;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtensionRule;
import org.camunda.optimize.test.it.extension.EngineDatabaseExtensionRule;
import org.camunda.optimize.test.it.extension.EngineIntegrationExtensionRule;
import org.camunda.optimize.test.util.ProcessReportDataBuilder;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.RequestOptions;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createCombinedReport;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.COMBINED_REPORT_INDEX_NAME;
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

  @RegisterExtension
  @Order(1)
  public ElasticSearchIntegrationTestExtensionRule elasticSearchIntegrationTestExtensionRule = new ElasticSearchIntegrationTestExtensionRule();
  @RegisterExtension
  @Order(2)
  public EngineIntegrationExtensionRule engineIntegrationExtensionRule = new EngineIntegrationExtensionRule();
  @RegisterExtension
  @Order(3)
  public EmbeddedOptimizeExtensionRule embeddedOptimizeExtensionRule = new EmbeddedOptimizeExtensionRule();
  @RegisterExtension
  @Order(4)
  public EngineDatabaseExtensionRule engineDatabaseExtensionRule = new EngineDatabaseExtensionRule(engineIntegrationExtensionRule.getEngineName());

  private AuthorizationClient authorizationClient = new AuthorizationClient(engineIntegrationExtensionRule);

  @AfterEach
  public void cleanUp() {
    LocalDateUtil.reset();
  }

  @Test
  public void reportIsWrittenToElasticsearch() throws IOException {
    // given
    String id = createNewCombinedReport();

    // then
    GetRequest getRequest = new GetRequest(COMBINED_REPORT_INDEX_NAME).id(id);
    GetResponse getResponse = elasticSearchIntegrationTestExtensionRule.getOptimizeElasticClient().get(getRequest, RequestOptions.DEFAULT);

    assertThat(getResponse.isExists(), is(true));
    CombinedReportDefinitionDto definitionDto = elasticSearchIntegrationTestExtensionRule.getObjectMapper()
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
    String singleReportId = createNewSingleReport(new SingleProcessReportDefinitionDto());
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
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    final String shouldNotBeUpdatedString = "shouldNotBeUpdated";
    String id = createNewCombinedReport();
    String singleReportId = createNewSingleNumberReport(engineDto);
    CombinedReportDefinitionDto report = new CombinedReportDefinitionDto();
    report.setData(createCombinedReport(singleReportId));
    report.getData().getConfiguration().setXLabel("FooXLabel");
    report.setId(shouldNotBeUpdatedString);
    report.setLastModifier("shouldNotBeUpdatedManually");
    report.setName("MyReport");
    OffsetDateTime shouldBeIgnoredDate = OffsetDateTime.now().plusHours(1);
    report.setCreated(shouldBeIgnoredDate);
    report.setLastModified(shouldBeIgnoredDate);
    report.setOwner(shouldNotBeUpdatedString);

    // when
    updateReport(id, report);
    List<ReportDefinitionDto> reports = getAllReports();

    // then
    assertThat(reports.size(), is(2));
    CombinedReportDefinitionDto newReport = (CombinedReportDefinitionDto) reports.stream()
      .filter(r -> r instanceof CombinedReportDefinitionDto).findFirst().get();
    assertThat(newReport.getData().getReportIds().isEmpty(), is(false));
    assertThat(newReport.getData().getReportIds().get(0), is(singleReportId));
    assertThat(newReport.getData().getConfiguration().getXLabel(), is("FooXLabel"));
    assertThat(newReport.getData().getVisualization(), is(ProcessVisualization.NUMBER));
    assertThat(newReport.getId(), is(id));
    assertThat(newReport.getCreated(), is(not(shouldBeIgnoredDate)));
    assertThat(newReport.getLastModified(), is(not(shouldBeIgnoredDate)));
    assertThat(newReport.getName(), is("MyReport"));
    assertThat(newReport.getOwner(), is(DEFAULT_USERNAME));
  }

  @Test
  public void updateCombinedReportCollectionReportCanBeAddedToSameCollectionCombinedReport() {
    // given
    String collectionId = addEmptyCollectionToOptimize();
    String combinedReportId = createNewCombinedReportInCollection(collectionId);
    final String singleReportId = addEmptySingleProcessReportToCollection(collectionId);

    // when
    final Response updateResponse = addSingleReportToCombinedReport(combinedReportId, singleReportId);

    // then
    assertThat(updateResponse.getStatus(), is(204));
  }

  @Test
  public void updateCombinedReportCollectionReportCannotBeAddedToOtherCollectionCombinedReport() {
    // given
    String collectionId1 = addEmptyCollectionToOptimize();
    String collectionId2 = addEmptyCollectionToOptimize();
    String combinedReportId = createNewCombinedReportInCollection(collectionId1);
    final String singleReportId = addEmptySingleProcessReportToCollection(collectionId2);

    // when
    final Response updateResponse = addSingleReportToCombinedReport(combinedReportId, singleReportId);

    // then
    assertThat(updateResponse.getStatus(), is(400));
  }

  @Test
  public void updateCombinedReportCollectionReportCannotBeAddedToPrivateCombinedReport() {
    // given
    String collectionId = addEmptyCollectionToOptimize();
    String combinedReportId = createNewCombinedReport();
    final String singleReportId = addEmptySingleProcessReportToCollection(collectionId);

    // when
    final Response updateResponse = addSingleReportToCombinedReport(combinedReportId, singleReportId);

    // then
    assertThat(updateResponse.getStatus(), is(400));
  }

  @Test
  public void updatePrivateCombinedReportReportCannotBeAddedToCollectionCombinedReport() {
    // given
    String collectionId = addEmptyCollectionToOptimize();
    String combinedReportId = createNewCombinedReportInCollection(collectionId);
    final String singleReportId = addEmptySingleProcessReportToCollection(null);

    // when
    final Response updateResponse = addSingleReportToCombinedReport(combinedReportId, singleReportId);

    // then
    assertThat(updateResponse.getStatus(), is(400));
  }

  @Test
  public void updatePrivateCombinedReportAddingOtherUsersPrivateReportFails() {
    //given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();

    String combinedReportId = createNewCombinedReportInCollection(null);
    final String reportId = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildCreateSingleProcessReportRequest()
      .execute(IdDto.class, 200)
      .getId();

    // when
    final Response updateResponse = addSingleReportToCombinedReport(combinedReportId, reportId);

    // then
    assertThat(updateResponse.getStatus(), is(403));
  }

  @Test
  public void addUncombinableReportThrowsError() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String numberReportId = createNewSingleNumberReport(engineDto);
    String rawReportId = createNewSingleRawReport(engineDto);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    String combinedReportId = createNewCombinedReport();
    CombinedReportDefinitionDto combinedReport = new CombinedReportDefinitionDto();
    combinedReport.setData(createCombinedReport(numberReportId, rawReportId));
    Response response = getUpdateCombinedProcessReportResponse(combinedReportId, combinedReport, true);

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
    updateReport(reportId, report);

    // when
    AuthorizedCombinedReportEvaluationResultDto<ReportMapResultDto> result = evaluateCombinedReportById(reportId);

    // then
    assertThat(result.getReportDefinition().getId(), is(reportId));
    assertThat(result.getReportDefinition().getName(), is("name"));
    assertThat(result.getReportDefinition().getOwner(), is(DEFAULT_USERNAME));
    assertThat(
      result.getReportDefinition().getCreated().truncatedTo(ChronoUnit.DAYS),
      is(now.truncatedTo(ChronoUnit.DAYS))
    );
    assertThat(result.getReportDefinition().getLastModifier(), is(DEFAULT_USERNAME));
    assertThat(
      result.getReportDefinition().getLastModified().truncatedTo(ChronoUnit.DAYS),
      is(now.truncatedTo(ChronoUnit.DAYS))
    );
    assertThat(result.getResult().getData(), is(notNullValue()));
    assertThat(result.getReportDefinition().getData().getReportIds().size(), is(1));
    assertThat(result.getReportDefinition().getData().getReportIds().get(0), is(singleReportId));
    assertThat(
      result.getReportDefinition().getData().getConfiguration(),
      equalTo(new CombinedReportConfigurationDto())
    );
  }

  @Test
  public void deleteCombinedReport() {
    // given
    String reportId = createNewCombinedReport();

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
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    String reportId = createNewCombinedReport(singleReportId, singleReportId2);
    AuthorizedCombinedReportEvaluationResultDto<ReportMapResultDto> result = evaluateCombinedReportById(reportId);

    // then
    assertThat(result.getReportDefinition().getId(), is(reportId));
    Map<String, AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto>> resultMap = result.getResult()
      .getData();
    assertThat(resultMap.size(), is(2));
    List<MapResultEntryDto> flowNodeToCount = resultMap.get(singleReportId).getResult().getData();
    assertThat(flowNodeToCount.size(), is(3));
    List<MapResultEntryDto> flowNodeToCount2 = resultMap.get(singleReportId2).getResult().getData();
    assertThat(flowNodeToCount2.size(), is(3));
  }

  @Test
  public void canSaveAndEvaluateCombinedReportsWithUserTaskDurationReportsOfDifferentDurationViewProperties() {
    // given
    ProcessInstanceEngineDto engineDto = deployAndStartSimpleUserTaskProcess();
    engineIntegrationExtensionRule.finishAllRunningUserTasks();
    String totalDurationReportId = createNewSingleUserTaskTotalDurationMapReport(engineDto);
    String idleDurationReportId = createNewSingleUserTaskIdleDurationMapReport(engineDto);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    String reportId = createNewCombinedReport(totalDurationReportId, idleDurationReportId);
    AuthorizedCombinedReportEvaluationResultDto<ReportMapResultDto> result = evaluateCombinedReportById(reportId);

    // then
    assertThat(result.getReportDefinition().getId(), is(reportId));
    Map<String, AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto>> resultMap =
      result.getResult().getData();
    assertThat(resultMap.size(), is(2));
    List<MapResultEntryDto> userTaskCount1 = resultMap.get(totalDurationReportId)
      .getResult()
      .getData();
    assertThat(userTaskCount1.size(), is(1));
    List<MapResultEntryDto> userTaskCount2 = resultMap.get(idleDurationReportId)
      .getResult()
      .getData();
    assertThat(userTaskCount2.size(), is(1));
  }

  @Test
  public void canSaveAndEvaluateCombinedReportsWithUserTaskDurationAndProcessDurationReports() {
    // given
    ProcessInstanceEngineDto engineDto = deployAndStartSimpleUserTaskProcess();
    engineIntegrationExtensionRule.finishAllRunningUserTasks();
    String userTaskTotalDurationReportId = createNewSingleUserTaskTotalDurationMapReport(engineDto);
    String flowNodeDurationReportId = createNewSingleDurationMapReport(engineDto);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    String reportId = createNewCombinedReport(userTaskTotalDurationReportId, flowNodeDurationReportId);
    AuthorizedCombinedReportEvaluationResultDto<ReportMapResultDto> result = evaluateCombinedReportById(reportId);

    // then
    assertThat(result.getReportDefinition().getId(), is(reportId));
    Map<String, AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto>> resultMap =
      result.getResult().getData();
    assertThat(resultMap.size(), is(2));
    List<MapResultEntryDto> userTaskCount1 = resultMap.get(userTaskTotalDurationReportId)
      .getResult()
      .getData();
    assertThat(userTaskCount1.size(), is(1));
    List<MapResultEntryDto> userTaskCount2 = resultMap.get(flowNodeDurationReportId)
      .getResult()
      .getData();
    assertThat(userTaskCount2.size(), is(3));
  }

  @Test
  public void canSaveAndEvaluateCombinedReportsWithStartAndEndDateGroupedReports() throws SQLException {
    // given
    OffsetDateTime now = OffsetDateTime.now();
    ProcessInstanceEngineDto engineDto = deployAndStartSimpleUserTaskProcess();
    engineIntegrationExtensionRule.finishAllRunningUserTasks(engineDto.getId());
    engineDatabaseExtensionRule.changeProcessInstanceStartDate(engineDto.getId(), now.minusDays(2L));

    engineIntegrationExtensionRule.startProcessInstance(engineDto.getDefinitionId());

    String singleReportId1 = createNewSingleReportGroupByEndDate(engineDto, GroupByDateUnit.DAY);
    String singleReportId2 = createNewSingleReportGroupByStartDate(engineDto, GroupByDateUnit.DAY);

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    final String combinedReportId = createNewCombinedReport(singleReportId1, singleReportId2);
    final AuthorizedCombinedReportEvaluationResultDto<ReportMapResultDto>
      result = evaluateCombinedReportById(combinedReportId);


    // then
    final Map<String, AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto>> resultMap = result.getResult()
      .getData();
    assertThat(resultMap, is(CoreMatchers.notNullValue()));
    assertThat(resultMap.size(), is(2));
    assertThat(resultMap.keySet(), contains(singleReportId1, singleReportId2));

    final ReportMapResultDto result1 = resultMap.get(singleReportId1)
      .getResult();
    final List<MapResultEntryDto> resultData1 = result1.getData();
    assertThat(resultData1, is(CoreMatchers.notNullValue()));
    assertThat(resultData1.size(), is(1));

    final ReportMapResultDto result2 = resultMap.get(singleReportId2)
      .getResult();
    final List<MapResultEntryDto> resultData2 = result2.getData();
    assertThat(resultData2, is(CoreMatchers.notNullValue()));
    assertThat(resultData2.size(), is(3));
  }

  @Test
  public void reportsThatCantBeEvaluatedAreIgnored() {
    // given
    deploySimpleServiceTaskProcessDefinition();
    String singleReportId = createNewSingleReport(new SingleProcessReportDefinitionDto());
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    String reportId = createNewCombinedReport(singleReportId);
    AuthorizedCombinedReportEvaluationResultDto<ReportMapResultDto> result = evaluateCombinedReportById(reportId);

    // then
    assertThat(result.getReportDefinition().getId(), is(reportId));
    Map<String, AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto>> resultMap =
      result.getResult().getData();
    assertThat(resultMap.size(), is(0));
  }

  @Test
  public void deletedSingleReportsAreRemovedFromCombinedReportWhenForced() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String singleReportIdToDelete = createNewSingleMapReport(engineDto);
    String remainingSingleReportId = createNewSingleMapReport(engineDto);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

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
    ProcessReportDataDto countFlowNodeFrequencyGroupByFlowNode = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setReportDataType(ProcessReportDataType.COUNT_FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE)
      .build();
    String singleReportIdToUpdate = createNewSingleMapReport(countFlowNodeFrequencyGroupByFlowNode);
    String remainingSingleReportId = createNewSingleMapReport(engineDto);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

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
    ProcessReportDataDto countFlowNodeFrequencyGroupByFlowNode = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setReportDataType(ProcessReportDataType.COUNT_FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE)
      .build();
    String singleReportIdToUpdate = createNewSingleMapReport(countFlowNodeFrequencyGroupByFlowNode);
    String remainingSingleReportId = createNewSingleMapReport(engineDto);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

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
    ProcessReportDataDto countFlowNodeFrequencyGroupByFlowNode = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setReportDataType(ProcessReportDataType.COUNT_FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE)
      .build();
    String singleReportIdToUpdate = createNewSingleMapReport(countFlowNodeFrequencyGroupByFlowNode);
    String remainingSingleReportId = createNewSingleMapReport(engineDto);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

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
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    AuthorizedCombinedReportEvaluationResultDto<ReportMapResultDto> result =
      evaluateUnsavedCombined(createCombinedReport(singleReportId, singleReportId2));

    // then
    Map<String, AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto>> resultMap =
      result.getResult().getData();
    assertThat(resultMap.size(), is(2));
    List<MapResultEntryDto> flowNodeToCount = resultMap.get(singleReportId).getResult().getData();
    assertThat(flowNodeToCount.size(), is(3));
    List<MapResultEntryDto> flowNodeToCount2 = resultMap.get(singleReportId2).getResult().getData();
    assertThat(flowNodeToCount2.size(), is(3));
  }

  @Test
  public void evaluationResultContainsSingleResultMetaData() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String singleReportId = createNewSingleMapReport(engineDto);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    AuthorizedCombinedReportEvaluationResultDto<ReportMapResultDto> result =
      evaluateUnsavedCombined(createCombinedReport(singleReportId));

    // then
    Map<String, AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto>> resultMap =
      result.getResult().getData();
    assertThat(resultMap.size(), is(1));
    AuthorizedEvaluationResultDto<ReportMapResultDto, SingleProcessReportDefinitionDto> mapResult =
      resultMap.get(singleReportId);
    assertThat(mapResult.getReportDefinition().getName(), is(TEST_REPORT_NAME));
  }

  @Test
  public void canEvaluateUnsavedCombinedReportWithSingleNumberReports() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String singleReportId1 = createNewSingleNumberReport(engineDto);
    String singleReportId2 = createNewSingleNumberReport(engineDto);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    AuthorizedCombinedReportEvaluationResultDto<NumberResultDto> result = evaluateUnsavedCombined(
      createCombinedReport(singleReportId1, singleReportId2)
    );

    // then
    Map<String, AuthorizedProcessReportEvaluationResultDto<NumberResultDto>> resultMap =
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
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    AuthorizedCombinedReportEvaluationResultDto<ReportMapResultDto> result = evaluateUnsavedCombined(
      createCombinedReport(singleReportId1, singleReportId2)
    );

    // then
    Map<String, AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto>> resultMap =
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
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    AuthorizedCombinedReportEvaluationResultDto<NumberResultDto> result = evaluateUnsavedCombined(
      createCombinedReport(singleReportId, singleReportId2)
    );

    // then
    Map<String, AuthorizedProcessReportEvaluationResultDto<NumberResultDto>> resultMap =
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
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    AuthorizedCombinedReportEvaluationResultDto<NumberResultDto> result = evaluateUnsavedCombined(
      createCombinedReport(singleReportId, singleReportId2)
    );

    // then
    Map<String, AuthorizedProcessReportEvaluationResultDto<NumberResultDto>> resultMap =
      result.getResult().getData();
    assertThat(resultMap.size(), is(2));
    assertThat(resultMap.keySet(), contains(singleReportId, singleReportId2));
  }

  @Test
  public void canEvaluateUnsavedCombinedReportWithProcessUserTaskTotalDurationMapReports() {
    // given
    ProcessInstanceEngineDto engineDto = deployAndStartSimpleUserTaskProcess();
    engineIntegrationExtensionRule.finishAllRunningUserTasks();
    String totalDurationReportId = createNewSingleUserTaskTotalDurationMapReport(engineDto);
    String totalDurationReportId2 = createNewSingleUserTaskTotalDurationMapReport(engineDto);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    AuthorizedCombinedReportEvaluationResultDto<NumberResultDto> result = evaluateUnsavedCombined(
      createCombinedReport(totalDurationReportId, totalDurationReportId2)
    );

    // then
    Map<String, AuthorizedProcessReportEvaluationResultDto<NumberResultDto>> resultMap =
      result.getResult().getData();
    assertThat(resultMap.size(), is(2));
    assertThat(resultMap.keySet(), contains(totalDurationReportId, totalDurationReportId2));
  }

  @Test
  public void canEvaluateUnsavedCombinedReportWithProcessUserTaskTotalDurationAndUserTaskIdleDurationMapReports() {
    // given
    ProcessInstanceEngineDto engineDto = deployAndStartSimpleUserTaskProcess();
    engineIntegrationExtensionRule.finishAllRunningUserTasks();
    String totalDurationReportId = createNewSingleUserTaskTotalDurationMapReport(engineDto);
    String idleDurationReportId = createNewSingleUserTaskIdleDurationMapReport(engineDto);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    AuthorizedCombinedReportEvaluationResultDto<NumberResultDto> result = evaluateUnsavedCombined(
      createCombinedReport(totalDurationReportId, idleDurationReportId)
    );

    // then
    Map<String, AuthorizedProcessReportEvaluationResultDto<NumberResultDto>> resultMap =
      result.getResult().getData();
    assertThat(resultMap.size(), is(2));
    assertThat(resultMap.keySet(), contains(totalDurationReportId, idleDurationReportId));
  }

  @Test
  public void canEvaluateUnsavedCombinedReportWithProcessDurationMapReportAndUserTaskTotalDurationMapReport() {
    // given
    ProcessInstanceEngineDto engineDto = deployAndStartSimpleUserTaskProcess();
    engineIntegrationExtensionRule.finishAllRunningUserTasks();
    String totalDurationReportId = createNewSingleDurationMapReport(engineDto);
    String idleDurationReportId = createNewSingleUserTaskIdleDurationMapReport(engineDto);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    AuthorizedCombinedReportEvaluationResultDto<NumberResultDto> result = evaluateUnsavedCombined(
      createCombinedReport(totalDurationReportId, idleDurationReportId)
    );

    // then
    Map<String, AuthorizedProcessReportEvaluationResultDto<NumberResultDto>> resultMap =
      result.getResult().getData();
    assertThat(resultMap.size(), is(2));
    assertThat(resultMap.keySet(), contains(totalDurationReportId, idleDurationReportId));
  }

  @Test
  public void canEvaluateUnsavedCombinedReportWithStartAndEndDateGroupedReports() throws SQLException {
    // given
    OffsetDateTime now = OffsetDateTime.now();
    ProcessInstanceEngineDto engineDto = deployAndStartSimpleUserTaskProcess();
    engineIntegrationExtensionRule.finishAllRunningUserTasks(engineDto.getId());
    engineDatabaseExtensionRule.changeProcessInstanceStartDate(engineDto.getId(), now.minusDays(2L));

    engineIntegrationExtensionRule.startProcessInstance(engineDto.getDefinitionId());

    String singleReportId1 = createNewSingleReportGroupByEndDate(engineDto, GroupByDateUnit.DAY);
    String singleReportId2 = createNewSingleReportGroupByStartDate(engineDto, GroupByDateUnit.DAY);

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    final AuthorizedCombinedReportEvaluationResultDto<ReportMapResultDto> result = evaluateUnsavedCombined(
      createCombinedReport(singleReportId1, singleReportId2));

    // then
    final Map<String, AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto>> resultMap = result.getResult()
      .getData();
    assertThat(resultMap, is(CoreMatchers.notNullValue()));
    assertThat(resultMap.keySet(), contains(singleReportId1, singleReportId2));

    final ReportMapResultDto result1 = resultMap.get(singleReportId1)
      .getResult();
    final List<MapResultEntryDto> resultData1 = result1.getData();
    assertThat(resultData1, is(CoreMatchers.notNullValue()));
    assertThat(resultData1.size(), is(1));

    final ReportMapResultDto result2 = resultMap.get(singleReportId2)
      .getResult();
    final List<MapResultEntryDto> resultData2 = result2.getData();
    assertThat(resultData2, is(CoreMatchers.notNullValue()));
    assertThat(resultData2.size(), is(3));
  }

  @Test
  public void canEvaluateUnsavedCombinedReportWithSingleNumberAndMapReport_firstWins() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String singleReportId = createNewSingleNumberReport(engineDto);
    String singleReportId2 = createNewSingleMapReport(engineDto);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    AuthorizedCombinedReportEvaluationResultDto<NumberResultDto> result = evaluateUnsavedCombined(
      createCombinedReport(singleReportId, singleReportId2)
    );

    // then
    Map<String, AuthorizedProcessReportEvaluationResultDto<NumberResultDto>> resultMap =
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
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    AuthorizedCombinedReportEvaluationResultDto<ReportMapResultDto> result =
      evaluateUnsavedCombined(createCombinedReport(singleReportId, singleReportId2));

    // then
    Map<String, AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto>> resultMap =
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
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    Response response =
      evaluateUnsavedCombinedReportAndReturnResponse(createCombinedReport(combinedReportId, singleReportId2));

    // then
    assertThat(response.getStatus(), is(404));
  }

  @Test
  public void combinedReportWithHyperMapReportCanBeEvaluated() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String combinedReportId = createNewCombinedReport();
    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setReportDataType(ProcessReportDataType.USER_TASK_FREQUENCY_GROUP_BY_ASSIGNEE_BY_USER_TASK)
      .build();
    String singleReportId = createNewSingleMapReport(reportData);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    AuthorizedCombinedReportEvaluationResultDto<ReportMapResultDto> result =
      evaluateUnsavedCombined(createCombinedReport(singleReportId));

    // then
    Map<String, AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto>> resultMap =
      result.getResult().getData();
    assertThat(resultMap.size(), is(0));
  }

  private String createNewSingleMapReport(ProcessInstanceEngineDto engineDto) {
    ProcessReportDataDto countFlowNodeFrequencyGroupByFlowNode = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setReportDataType(ProcessReportDataType.COUNT_FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE)
      .build();
    return createNewSingleMapReport(countFlowNodeFrequencyGroupByFlowNode);
  }

  private String createNewSingleDurationNumberReport(ProcessInstanceEngineDto engineDto) {
    ProcessReportDataDto durationReportData = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setReportDataType(ProcessReportDataType.PROC_INST_DUR_GROUP_BY_NONE)
      .build();
    return createNewSingleNumberReport(durationReportData);
  }

  private String createNewSingleDurationMapReport(ProcessInstanceEngineDto engineDto) {
    ProcessReportDataDto durationMapReportData = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setReportDataType(ProcessReportDataType.FLOW_NODE_DUR_GROUP_BY_FLOW_NODE)
      .setVisualization(ProcessVisualization.TABLE)
      .build();
    return createNewSingleMapReport(durationMapReportData);
  }

  private String createNewSingleUserTaskTotalDurationMapReport(ProcessInstanceEngineDto engineDto) {
    ProcessReportDataDto durationMapReportData = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setUserTaskDurationTime(UserTaskDurationTime.TOTAL)
      .setReportDataType(ProcessReportDataType.USER_TASK_DURATION_GROUP_BY_FLOW_NODE)
      .setVisualization(ProcessVisualization.TABLE)
      .build();
    return createNewSingleMapReport(durationMapReportData);
  }

  private String createNewSingleUserTaskIdleDurationMapReport(ProcessInstanceEngineDto engineDto) {
    ProcessReportDataDto durationMapReportData = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setUserTaskDurationTime(UserTaskDurationTime.IDLE)
      .setReportDataType(ProcessReportDataType.USER_TASK_DURATION_GROUP_BY_FLOW_NODE)
      .build();
    return createNewSingleMapReport(durationMapReportData);
  }

  private String createNewSingleMapReport(ProcessReportDataDto data) {
    SingleProcessReportDefinitionDto singleProcessReportDefinitionDto = new SingleProcessReportDefinitionDto();
    singleProcessReportDefinitionDto.setName(TEST_REPORT_NAME);
    singleProcessReportDefinitionDto.setData(data);
    return createNewSingleReport(singleProcessReportDefinitionDto);
  }

  private String createNewSingleNumberReport(ProcessInstanceEngineDto engineDto) {
    ProcessReportDataDto countFlowNodeFrequencyGroupByFlowNode = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setReportDataType(ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_NONE)
      .build();
    return createNewSingleNumberReport(countFlowNodeFrequencyGroupByFlowNode);
  }

  private String createNewSingleNumberReport(ProcessReportDataDto data) {
    SingleProcessReportDefinitionDto singleProcessReportDefinitionDto = new SingleProcessReportDefinitionDto();
    singleProcessReportDefinitionDto.setData(data);
    return createNewSingleReport(singleProcessReportDefinitionDto);
  }

  private String createNewSingleReportGroupByEndDate(ProcessInstanceEngineDto engineDto,
                                                     GroupByDateUnit groupByDateUnit) {
    ProcessReportDataDto reportDataByEndDate = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setDateInterval(groupByDateUnit)
      .setReportDataType(ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_END_DATE)
      .build();
    return createNewSingleMapReport(reportDataByEndDate);
  }

  private String createNewSingleReportGroupByStartDate(ProcessInstanceEngineDto engineDto,
                                                       GroupByDateUnit groupByDateUnit) {
    ProcessReportDataDto reportDataByStartDate = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setDateInterval(groupByDateUnit)
      .setReportDataType(ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_START_DATE)
      .build();
    return createNewSingleMapReport(reportDataByStartDate);
  }


  private String createNewSingleRawReport(ProcessInstanceEngineDto engineDto) {
    ProcessReportDataDto countFlowNodeFrequencyGroupByFlowNode = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setReportDataType(ProcessReportDataType.RAW_DATA)
      .build();
    SingleProcessReportDefinitionDto singleProcessReportDefinitionDto = new SingleProcessReportDefinitionDto();
    singleProcessReportDefinitionDto.setData(countFlowNodeFrequencyGroupByFlowNode);
    return createNewSingleReport(singleProcessReportDefinitionDto);
  }

  private String createNewCombinedReport(String... singleReportIds) {
    CombinedReportDefinitionDto report = new CombinedReportDefinitionDto();
    report.setData(createCombinedReport(singleReportIds));
    return createNewCombinedReport(report);
  }

  private String createNewCombinedReport(CombinedReportDefinitionDto report) {
    String reportId = createNewCombinedReportInCollection(null);
    updateReport(reportId, report);
    return reportId;
  }

  private String createNewCombinedReportInCollection(String collectionId) {
    CombinedReportDefinitionDto combinedReportDefinitionDto = new CombinedReportDefinitionDto();
    combinedReportDefinitionDto.setCollectionId(collectionId);
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildCreateCombinedReportRequest(combinedReportDefinitionDto)
      .execute(IdDto.class, 200)
      .getId();
  }

  private ProcessInstanceEngineDto deploySimpleServiceTaskProcessDefinition() {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("aProcess")
      .startEvent(START_EVENT)
      .serviceTask(SERVICE_TASK_ID)
      .camundaExpression("${true}")
      .endEvent(END_EVENT)
      .done();
    return engineIntegrationExtensionRule.deployAndStartProcess(modelInstance);
  }

  private ProcessInstanceEngineDto deployAndStartSimpleUserTaskProcess() {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .startEvent("startEvent")
      .userTask("userTask")
      .endEvent()
      .done();
    return engineIntegrationExtensionRule.deployAndStartProcess(processModel);
  }

  private void deleteReport(String reportId) {
    deleteReport(reportId, null);
  }

  private void deleteReport(String reportId, Boolean force) {
    Response response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildDeleteReportRequest(reportId, force)
      .execute();

    // then the status code is okay
    assertThat(response.getStatus(), is(204));
  }

  private String createNewSingleReport(SingleProcessReportDefinitionDto singleProcessReportDefinitionDto) {
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest(singleProcessReportDefinitionDto)
      .execute(IdDto.class, 200)
      .getId();
  }

  private void updateReport(String id, SingleProcessReportDefinitionDto updatedReport, Boolean force) {
    Response response = getUpdateSingleProcessReportResponse(id, updatedReport, force);
    assertThat(response.getStatus(), is(204));
  }

  private void updateReport(String id, CombinedReportDefinitionDto updatedReport) {
    updateReport(id, updatedReport, null);
  }

  private void updateReport(String id, CombinedReportDefinitionDto updatedReport, Boolean force) {
    Response response = getUpdateCombinedProcessReportResponse(id, updatedReport, force);
    assertThat(response.getStatus(), is(204));
  }

  private Response getUpdateSingleProcessReportResponse(String id, SingleProcessReportDefinitionDto updatedReport,
                                                        Boolean force) {
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildUpdateSingleProcessReportRequest(id, updatedReport, force)
      .execute();
  }

  private Response getUpdateCombinedProcessReportResponse(String id, CombinedReportDefinitionDto updatedReport,
                                                          Boolean force) {
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildUpdateCombinedProcessReportRequest(id, updatedReport, force)
      .execute();
  }

  private <T extends SingleReportResultDto> AuthorizedCombinedReportEvaluationResultDto<T> evaluateCombinedReportById(String reportId) {
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildEvaluateSavedReportRequest(reportId)
      // @formatter:off
      .execute(new TypeReference<AuthorizedCombinedReportEvaluationResultDto<T>>() {});
      // @formatter:on
  }

  private <T extends SingleReportResultDto> AuthorizedCombinedReportEvaluationResultDto<T> evaluateUnsavedCombined(CombinedReportDataDto reportDataDto) {
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildEvaluateCombinedUnsavedReportRequest(reportDataDto)
      // @formatter:off
      .execute(new TypeReference<AuthorizedCombinedReportEvaluationResultDto<T>>() {});
      // @formatter:on
  }

  private Response evaluateUnsavedCombinedReportAndReturnResponse(CombinedReportDataDto reportDataDto) {
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildEvaluateCombinedUnsavedReportRequest(reportDataDto)
      .execute();
  }

  private List<ReportDefinitionDto> getAllReports() {
    return getAllReportsWithQueryParam(new HashMap<>());
  }

  private List<ReportDefinitionDto> getAllReportsWithQueryParam(Map<String, Object> queryParams) {
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .addQueryParams(queryParams)
      .buildGetAllReportsRequest()
      .executeAndReturnList(ReportDefinitionDto.class, 200);
  }

  private String addEmptySingleProcessReportToCollection(final String collectionId) {
    SingleProcessReportDefinitionDto singleProcessReportDefinitionDto = new SingleProcessReportDefinitionDto();
    singleProcessReportDefinitionDto.setCollectionId(collectionId);
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest(singleProcessReportDefinitionDto)
      .execute(IdDto.class, 200)
      .getId();
  }

  private String addEmptyCollectionToOptimize() {
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildCreateCollectionRequest()
      .execute(IdDto.class, 200)
      .getId();
  }

  private Response addSingleReportToCombinedReport(final String combinedReportId, final String reportId) {
    final CombinedReportDefinitionDto combinedReportData = new CombinedReportDefinitionDto();
    combinedReportData.getData().getReports().add(new CombinedReportItemDto(reportId, "red"));
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildUpdateCombinedProcessReportRequest(combinedReportId, combinedReportData)
      .execute();
  }
}
