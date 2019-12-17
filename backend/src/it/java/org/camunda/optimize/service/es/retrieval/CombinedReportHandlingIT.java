/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.retrieval;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.http.HttpStatus;
import org.assertj.core.util.Lists;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.AbstractIT;
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
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.rest.ErrorResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedCombinedReportEvaluationResultDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedEvaluationResultDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.test.engine.AuthorizationClient;
import org.camunda.optimize.test.it.extension.EngineDatabaseExtension;
import org.camunda.optimize.test.util.ProcessReportDataBuilder;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.RequestOptions;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createCombinedReportData;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createCountProcessInstanceFrequencyGroupByEndDate;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createCountProcessInstanceFrequencyGroupByStartDate;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createCountProcessInstanceFrequencyGroupByVariable;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createFlowNodeDurationGroupByFlowNodeBarReport;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createProcessInstanceDurationGroupByStartDateReport;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createUserTaskIdleDurationMapGroupByFlowNodeReport;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.COMBINED_REPORT_INDEX_NAME;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.core.IsNull.notNullValue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CombinedReportHandlingIT extends AbstractIT {

  private static final String START_EVENT = "startEvent";
  private static final String END_EVENT = "endEvent";
  private static final String SERVICE_TASK_ID = "aSimpleServiceTask";
  private static final String TEST_REPORT_NAME = "My foo report";

  @RegisterExtension
  @Order(4)
  public EngineDatabaseExtension engineDatabaseExtension =
    new EngineDatabaseExtension(engineIntegrationExtension.getEngineName());

  private AuthorizationClient authorizationClient = new AuthorizationClient(engineIntegrationExtension);

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
    GetResponse getResponse = elasticSearchIntegrationTestExtension.getOptimizeElasticClient()
      .get(getRequest, RequestOptions.DEFAULT);

    assertThat(getResponse.isExists(), is(true));
    CombinedReportDefinitionDto definitionDto = elasticSearchIntegrationTestExtension.getObjectMapper()
      .readValue(getResponse.getSourceAsString(), CombinedReportDefinitionDto.class);
    assertThat(definitionDto.getData(), notNullValue());
    CombinedReportDataDto data = definitionDto.getData();
    assertThat(data.getConfiguration(), notNullValue());
    assertThat(data.getConfiguration(), equalTo(new CombinedReportConfigurationDto()));
    assertThat(definitionDto.getData().getReportIds(), notNullValue());
  }

  @ParameterizedTest
  @MethodSource("getUncombinableSingleReports")
  public void combineUncombinableSingleReports(List<SingleProcessReportDefinitionDto> singleReports) {
    //given
    CombinedReportDataDto combinedReportData = new CombinedReportDataDto();

    List<CombinedReportItemDto> reportIds = singleReports.stream()
      .map(r -> new CombinedReportItemDto(createNewSingleReport(r)))
      .collect(Collectors.toList());

    combinedReportData.setReports(reportIds);
    CombinedReportDefinitionDto combinedReport = new CombinedReportDefinitionDto();
    combinedReport.setData(combinedReportData);

    //when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateCombinedReportRequest(combinedReport)
      .execute();

    //then
    assertThat(response.getStatus(), is(HttpStatus.SC_BAD_REQUEST));
  }

  @ParameterizedTest
  @MethodSource("getCombinableSingleReports")
  public void combineCombinableSingleReports(List<SingleProcessReportDefinitionDto> singleReports) {
    //given
    CombinedReportDataDto combinedReportData = new CombinedReportDataDto();

    List<CombinedReportItemDto> reportIds = singleReports.stream()
      .map(r -> new CombinedReportItemDto(createNewSingleReport(r)))
      .collect(Collectors.toList());

    combinedReportData.setReports(reportIds);
    CombinedReportDefinitionDto combinedReport = new CombinedReportDefinitionDto();
    combinedReport.setData(combinedReportData);

    //when
    IdDto response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateCombinedReportRequest(combinedReport)
      .execute(IdDto.class, HttpStatus.SC_OK);

    //then
    AuthorizedCombinedReportEvaluationResultDto<SingleReportResultDto> result =
      evaluateCombinedReportById(response.getId());

    assertThat(result.getReportDefinition().getData().getReports(), containsInAnyOrder(reportIds.toArray()));
  }

  private static Stream<List<SingleProcessReportDefinitionDto>> getCombinableSingleReports() {
    //different procDefs
    SingleProcessReportDefinitionDto procDefKeyReport = new SingleProcessReportDefinitionDto();
    ProcessReportDataDto procDefKeyReportData = createCountProcessInstanceFrequencyGroupByStartDate(
      "key",
      Collections.singletonList("1"),
      GroupByDateUnit.YEAR
    );
    procDefKeyReportData.setVisualization(ProcessVisualization.BAR);
    procDefKeyReport.setData(procDefKeyReportData);

    SingleProcessReportDefinitionDto procDefAnotherKeyReport = new SingleProcessReportDefinitionDto();
    ProcessReportDataDto procDefAnotherKeyReportData = createCountProcessInstanceFrequencyGroupByStartDate(
      "anotherKey",
      Collections.singletonList("1"),
      GroupByDateUnit.YEAR
    );
    procDefAnotherKeyReportData.setVisualization(ProcessVisualization.BAR);
    procDefAnotherKeyReport.setData(procDefAnotherKeyReportData);

    //byStartDate/byEndDate
    SingleProcessReportDefinitionDto byEndDate = new SingleProcessReportDefinitionDto();
    ProcessReportDataDto byEndDateData = createCountProcessInstanceFrequencyGroupByEndDate(
      "key",
      Collections.singletonList("1"),
      GroupByDateUnit.YEAR
    );
    byEndDateData.setVisualization(ProcessVisualization.BAR);
    byEndDate.setData(byEndDateData);

    //userTaskDuration/flowNodeDuration
    SingleProcessReportDefinitionDto userTaskDuration = new SingleProcessReportDefinitionDto();
    ProcessReportDataDto userTaskDurationData = createUserTaskIdleDurationMapGroupByFlowNodeReport(
      "key",
      Collections.singletonList("1")
    );
    userTaskDurationData.setVisualization(ProcessVisualization.BAR);
    userTaskDuration.setData(userTaskDurationData);

    SingleProcessReportDefinitionDto flowNodeDuration = new SingleProcessReportDefinitionDto();
    ProcessReportDataDto flowNodeDurationData = createFlowNodeDurationGroupByFlowNodeBarReport(
      "key",
      Collections.singletonList("1")
    );
    flowNodeDurationData.setVisualization(ProcessVisualization.BAR);
    flowNodeDuration.setData(flowNodeDurationData);


    return Stream.of(
      Lists.newArrayList(procDefKeyReport, procDefAnotherKeyReport),
      Lists.newArrayList(byEndDate, procDefKeyReport),
      Lists.newArrayList(userTaskDuration, flowNodeDuration)
    );
  }

  private static Stream<List<SingleProcessReportDefinitionDto>> getUncombinableSingleReports() {
    //uncombinable visualization
    SingleProcessReportDefinitionDto PICount_startDateYear_bar = new SingleProcessReportDefinitionDto();
    ProcessReportDataDto PICount_startDateYear_barData = createCountProcessInstanceFrequencyGroupByStartDate(
      "key",
      Collections.singletonList("1"),
      GroupByDateUnit.YEAR
    );
    PICount_startDateYear_barData.setVisualization(ProcessVisualization.BAR);
    PICount_startDateYear_bar.setData(PICount_startDateYear_barData);

    SingleProcessReportDefinitionDto PICount_startDateYear_line = new SingleProcessReportDefinitionDto();
    ProcessReportDataDto PICount_startDateYear_lineData = createCountProcessInstanceFrequencyGroupByStartDate(
      "key",
      Collections.singletonList("1"),
      GroupByDateUnit.YEAR
    );
    PICount_startDateYear_lineData.setVisualization(ProcessVisualization.LINE);
    PICount_startDateYear_line.setData(PICount_startDateYear_lineData);

    //uncombinable groupBy
    ProcessReportDataDto PICount_byVariable_barData = createCountProcessInstanceFrequencyGroupByVariable(
      "key",
      Collections.singletonList("1"),
      "var",
      VariableType.BOOLEAN
    );
    PICount_byVariable_barData.setVisualization(ProcessVisualization.BAR);
    SingleProcessReportDefinitionDto PICount_byVariable_bar = new SingleProcessReportDefinitionDto();
    PICount_byVariable_bar.setData(PICount_byVariable_barData);

    //uncombinable view
    SingleProcessReportDefinitionDto PIDuration_startDateYear_bar = new SingleProcessReportDefinitionDto();
    ProcessReportDataDto PIDuration_startDateYear_barData = createProcessInstanceDurationGroupByStartDateReport(
      "key",
      Collections.singletonList("1"),
      GroupByDateUnit.YEAR
    );
    PIDuration_startDateYear_barData.setVisualization(ProcessVisualization.BAR);
    PIDuration_startDateYear_bar.setData(PIDuration_startDateYear_barData);

    return Stream.of(
      Lists.newArrayList(PICount_startDateYear_bar, PICount_startDateYear_line),
      Lists.newArrayList(PICount_byVariable_bar, PICount_startDateYear_bar),
      Lists.newArrayList(PICount_startDateYear_bar, PIDuration_startDateYear_bar)
    );
  }

  @Test
  public void getSingleAndCombinedReport() {
    // given
    String singleReportId = createNewSingleReport(new SingleProcessReportDefinitionDto());
    String combinedReportId = createNewCombinedReport();

    // when
    List<ReportDefinitionDto> reports = getAllPrivateReports();

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
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    final String shouldNotBeUpdatedString = "shouldNotBeUpdated";
    String id = createNewCombinedReport();
    String singleReportId = createNewSingleNumberReport(engineDto);
    CombinedReportDefinitionDto report = new CombinedReportDefinitionDto();
    report.setData(createCombinedReportData(singleReportId));
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
    List<ReportDefinitionDto> reports = getAllPrivateReports();

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

  private Stream<Function<CombinedReportUpdateData, Response>> reportUpdateScenarios() {
    return Stream.of(
      data -> {
        String combinedReportId = createNewCombinedReportInCollection(data.getCollectionId());
        return addSingleReportToCombinedReport(combinedReportId, data.getSingleReportId());
      },
      data -> {
        CombinedReportDataDto combinedReportData = new CombinedReportDataDto();
        combinedReportData.setReports(Collections.singletonList(new CombinedReportItemDto(data.getSingleReportId())));
        CombinedReportDefinitionDto combinedReport = new CombinedReportDefinitionDto();
        combinedReport.setData(combinedReportData);
        combinedReport.setCollectionId(data.getCollectionId());
        return embeddedOptimizeExtension
          .getRequestExecutor()
          .buildCreateCombinedReportRequest(combinedReport)
          .execute();
      }
    );
  }

  @ParameterizedTest
  @MethodSource("reportUpdateScenarios")
  public void updateCombinedReportCollectionReportCanBeAddedToSameCollectionCombinedReport(Function<CombinedReportUpdateData, Response> scenario) {
    // given
    String collectionId = addEmptyCollectionToOptimize();
    final String singleReportId = addEmptySingleProcessReportToCollection(collectionId);

    // when
    Response updateResponse = scenario.apply(new CombinedReportUpdateData(singleReportId, collectionId));

    // then
    assertThat(updateResponse.getStatus(), anyOf(equalTo(HttpStatus.SC_OK), equalTo(204)));
  }

  @ParameterizedTest
  @MethodSource("reportUpdateScenarios")
  public void updateCombinedReportCollectionReportCannotBeAddedToOtherCollectionCombinedReport(Function<CombinedReportUpdateData, Response> scenario) {
    // given
    String collectionId1 = addEmptyCollectionToOptimize();
    String collectionId2 = addEmptyCollectionToOptimize();
    final String singleReportId = addEmptySingleProcessReportToCollection(collectionId2);

    // when
    Response updateResponse = scenario.apply(new CombinedReportUpdateData(singleReportId, collectionId1));

    // then
    assertThat(updateResponse.getStatus(), is(400));
  }

  @ParameterizedTest
  @MethodSource("reportUpdateScenarios")
  public void updateCombinedReportCollectionReportCannotBeAddedToPrivateCombinedReport(Function<CombinedReportUpdateData, Response> scenario) {
    // given
    String collectionId = addEmptyCollectionToOptimize();
    final String singleReportId = addEmptySingleProcessReportToCollection(collectionId);

    // when
    Response updateResponse = scenario.apply(new CombinedReportUpdateData(singleReportId, null));

    // then
    assertThat(updateResponse.getStatus(), is(400));
  }

  @ParameterizedTest
  @MethodSource("reportUpdateScenarios")
  public void updatePrivateCombinedReportReportCannotBeAddedToCollectionCombinedReport(Function<CombinedReportUpdateData, Response> scenario) {
    // given
    String collectionId = addEmptyCollectionToOptimize();
    final String singleReportId = addEmptySingleProcessReportToCollection(null);

    // when
    Response updateResponse = scenario.apply(new CombinedReportUpdateData(singleReportId, collectionId));

    // then
    assertThat(updateResponse.getStatus(), is(400));
  }

  @ParameterizedTest
  @MethodSource("reportUpdateScenarios")
  public void updatePrivateCombinedReportAddingOtherUsersPrivateReportFails(Function<CombinedReportUpdateData,
    Response> scenario) {
    //given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();

    final String reportId = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildCreateSingleProcessReportRequest()
      .execute(IdDto.class, HttpStatus.SC_OK)
      .getId();

    // when
    Response updateResponse = scenario.apply(new CombinedReportUpdateData(reportId, null));


    // then
    assertThat(updateResponse.getStatus(), is(403));
  }

  @Test
  public void addUncombinableReportThrowsError() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String numberReportId = createNewSingleNumberReport(engineDto);
    String rawReportId = createNewSingleRawReport(engineDto);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    String combinedReportId = createNewCombinedReport();
    CombinedReportDefinitionDto combinedReport = new CombinedReportDefinitionDto();
    combinedReport.setData(createCombinedReportData(numberReportId, rawReportId));
    ErrorResponseDto response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdateCombinedProcessReportRequest(combinedReportId, combinedReport, true)
      .execute(ErrorResponseDto.class, HttpStatus.SC_BAD_REQUEST);

    // then
    assertThat(response.getErrorCode(), is("reportsNotCombinable"));
  }

  @Test
  public void reportEvaluationReturnsMetaData() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String singleReportId = createNewSingleMapReport(engineDto);
    String reportId = createNewCombinedReport();
    CombinedReportDefinitionDto report = new CombinedReportDefinitionDto();
    report.setData(createCombinedReportData(singleReportId));
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
    List<ReportDefinitionDto> reports = getAllPrivateReports();

    // then
    assertThat(reports.size(), is(0));
  }

  @Test
  public void canSaveAndEvaluateCombinedReports() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String singleReportId = createNewSingleMapReport(engineDto);
    String singleReportId2 = createNewSingleMapReport(engineDto);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

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
    engineIntegrationExtension.finishAllRunningUserTasks();
    String totalDurationReportId = createNewSingleUserTaskTotalDurationMapReport(engineDto);
    String idleDurationReportId = createNewSingleUserTaskIdleDurationMapReport(engineDto);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

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
    engineIntegrationExtension.finishAllRunningUserTasks();
    String userTaskTotalDurationReportId = createNewSingleUserTaskTotalDurationMapReport(engineDto);
    String flowNodeDurationReportId = createNewSingleDurationMapReport(engineDto);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

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
    engineIntegrationExtension.finishAllRunningUserTasks(engineDto.getId());
    engineDatabaseExtension.changeProcessInstanceStartDate(engineDto.getId(), now.minusDays(2L));

    engineIntegrationExtension.startProcessInstance(engineDto.getDefinitionId());

    String singleReportId1 = createNewSingleReportGroupByEndDate(engineDto, GroupByDateUnit.DAY);
    String singleReportId2 = createNewSingleReportGroupByStartDate(engineDto, GroupByDateUnit.DAY);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

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
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

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
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    String combinedReportId = createNewCombinedReport(singleReportIdToDelete, remainingSingleReportId);
    deleteReport(singleReportIdToDelete, true);
    List<ReportDefinitionDto> reports = getAllPrivateReports();

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
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    String combinedReportId = createNewCombinedReport(singleReportIdToUpdate, remainingSingleReportId);
    SingleProcessReportDefinitionDto report = new SingleProcessReportDefinitionDto();
    countFlowNodeFrequencyGroupByFlowNode.setVisualization(ProcessVisualization.TABLE);
    report.setData(countFlowNodeFrequencyGroupByFlowNode);
    updateReport(singleReportIdToUpdate, report, true);
    List<ReportDefinitionDto> reports = getAllPrivateReports();

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
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    String combinedReportId = createNewCombinedReport(singleReportIdToUpdate, remainingSingleReportId);
    SingleProcessReportDefinitionDto report = new SingleProcessReportDefinitionDto();
    countFlowNodeFrequencyGroupByFlowNode.getGroupBy().setType(ProcessGroupByType.START_DATE);
    report.setData(countFlowNodeFrequencyGroupByFlowNode);
    updateReport(singleReportIdToUpdate, report, true);
    List<ReportDefinitionDto> reports = getAllPrivateReports();

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
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    String combinedReportId = createNewCombinedReport(singleReportIdToUpdate, remainingSingleReportId);
    SingleProcessReportDefinitionDto report = new SingleProcessReportDefinitionDto();
    countFlowNodeFrequencyGroupByFlowNode.getView().setEntity(ProcessViewEntity.PROCESS_INSTANCE);
    report.setData(countFlowNodeFrequencyGroupByFlowNode);
    updateReport(singleReportIdToUpdate, report, true);
    List<ReportDefinitionDto> reports = getAllPrivateReports();

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
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    AuthorizedCombinedReportEvaluationResultDto<ReportMapResultDto> result =
      evaluateUnsavedCombined(createCombinedReportData(singleReportId, singleReportId2));

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
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    AuthorizedCombinedReportEvaluationResultDto<ReportMapResultDto> result =
      evaluateUnsavedCombined(createCombinedReportData(singleReportId));

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
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    AuthorizedCombinedReportEvaluationResultDto<NumberResultDto> result = evaluateUnsavedCombined(
      createCombinedReportData(singleReportId1, singleReportId2)
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
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    AuthorizedCombinedReportEvaluationResultDto<ReportMapResultDto> result = evaluateUnsavedCombined(
      createCombinedReportData(singleReportId1, singleReportId2)
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
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    AuthorizedCombinedReportEvaluationResultDto<NumberResultDto> result = evaluateUnsavedCombined(
      createCombinedReportData(singleReportId, singleReportId2)
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
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    AuthorizedCombinedReportEvaluationResultDto<NumberResultDto> result = evaluateUnsavedCombined(
      createCombinedReportData(singleReportId, singleReportId2)
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
    engineIntegrationExtension.finishAllRunningUserTasks();
    String totalDurationReportId = createNewSingleUserTaskTotalDurationMapReport(engineDto);
    String totalDurationReportId2 = createNewSingleUserTaskTotalDurationMapReport(engineDto);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    AuthorizedCombinedReportEvaluationResultDto<NumberResultDto> result = evaluateUnsavedCombined(
      createCombinedReportData(totalDurationReportId, totalDurationReportId2)
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
    engineIntegrationExtension.finishAllRunningUserTasks();
    String totalDurationReportId = createNewSingleUserTaskTotalDurationMapReport(engineDto);
    String idleDurationReportId = createNewSingleUserTaskIdleDurationMapReport(engineDto);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    AuthorizedCombinedReportEvaluationResultDto<NumberResultDto> result = evaluateUnsavedCombined(
      createCombinedReportData(totalDurationReportId, idleDurationReportId)
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
    engineIntegrationExtension.finishAllRunningUserTasks();
    String totalDurationReportId = createNewSingleDurationMapReport(engineDto);
    String idleDurationReportId = createNewSingleUserTaskIdleDurationMapReport(engineDto);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    AuthorizedCombinedReportEvaluationResultDto<NumberResultDto> result = evaluateUnsavedCombined(
      createCombinedReportData(totalDurationReportId, idleDurationReportId)
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
    engineIntegrationExtension.finishAllRunningUserTasks(engineDto.getId());
    engineDatabaseExtension.changeProcessInstanceStartDate(engineDto.getId(), now.minusDays(2L));

    engineIntegrationExtension.startProcessInstance(engineDto.getDefinitionId());

    String singleReportId1 = createNewSingleReportGroupByEndDate(engineDto, GroupByDateUnit.DAY);
    String singleReportId2 = createNewSingleReportGroupByStartDate(engineDto, GroupByDateUnit.DAY);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final AuthorizedCombinedReportEvaluationResultDto<ReportMapResultDto> result = evaluateUnsavedCombined(
      createCombinedReportData(singleReportId1, singleReportId2));

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
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    AuthorizedCombinedReportEvaluationResultDto<NumberResultDto> result = evaluateUnsavedCombined(
      createCombinedReportData(singleReportId, singleReportId2)
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
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    AuthorizedCombinedReportEvaluationResultDto<ReportMapResultDto> result =
      evaluateUnsavedCombined(createCombinedReportData(singleReportId, singleReportId2));

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
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    Response response =
      evaluateUnsavedCombinedReportAndReturnResponse(createCombinedReportData(combinedReportId, singleReportId2));

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
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    AuthorizedCombinedReportEvaluationResultDto<ReportMapResultDto> result =
      evaluateUnsavedCombined(createCombinedReportData(singleReportId));

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
    report.setData(createCombinedReportData(singleReportIds));
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
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateCombinedReportRequest(combinedReportDefinitionDto)
      .execute(IdDto.class, HttpStatus.SC_OK)
      .getId();
  }

  private ProcessInstanceEngineDto deploySimpleServiceTaskProcessDefinition() {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("aProcess")
      .startEvent(START_EVENT)
      .serviceTask(SERVICE_TASK_ID)
      .camundaExpression("${true}")
      .endEvent(END_EVENT)
      .done();
    return engineIntegrationExtension.deployAndStartProcess(modelInstance);
  }

  private ProcessInstanceEngineDto deployAndStartSimpleUserTaskProcess() {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .startEvent("startEvent")
      .userTask("userTask")
      .endEvent()
      .done();
    return engineIntegrationExtension.deployAndStartProcess(processModel);
  }

  private void deleteReport(String reportId) {
    deleteReport(reportId, null);
  }

  private void deleteReport(String reportId, Boolean force) {
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildDeleteReportRequest(reportId, force)
      .execute();

    // then the status code is okay
    assertThat(response.getStatus(), is(204));
  }

  private String createNewSingleReport(SingleProcessReportDefinitionDto singleProcessReportDefinitionDto) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest(singleProcessReportDefinitionDto)
      .execute(IdDto.class, HttpStatus.SC_OK)
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
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdateSingleProcessReportRequest(id, updatedReport, force)
      .execute();
  }

  private Response getUpdateCombinedProcessReportResponse(String id, CombinedReportDefinitionDto updatedReport,
                                                          Boolean force) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdateCombinedProcessReportRequest(id, updatedReport, force)
      .execute();
  }

  private <T extends SingleReportResultDto> AuthorizedCombinedReportEvaluationResultDto<T> evaluateCombinedReportById(String reportId) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSavedReportRequest(reportId)
      // @formatter:off
      .execute(new TypeReference<AuthorizedCombinedReportEvaluationResultDto<T>>() {});
      // @formatter:on
  }

  private <T extends SingleReportResultDto> AuthorizedCombinedReportEvaluationResultDto<T> evaluateUnsavedCombined(CombinedReportDataDto reportDataDto) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateCombinedUnsavedReportRequest(reportDataDto)
      // @formatter:off
      .execute(new TypeReference<AuthorizedCombinedReportEvaluationResultDto<T>>() {});
      // @formatter:on
  }

  private Response evaluateUnsavedCombinedReportAndReturnResponse(CombinedReportDataDto reportDataDto) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateCombinedUnsavedReportRequest(reportDataDto)
      .execute();
  }

  private List<ReportDefinitionDto> getAllPrivateReports() {
    return getAllPrivateReportsWithQueryParam(new HashMap<>());
  }

  private List<ReportDefinitionDto> getAllPrivateReportsWithQueryParam(Map<String, Object> queryParams) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .addQueryParams(queryParams)
      .buildGetAllPrivateReportsRequest()
      .executeAndReturnList(ReportDefinitionDto.class, HttpStatus.SC_OK);
  }

  private String addEmptySingleProcessReportToCollection(final String collectionId) {
    SingleProcessReportDefinitionDto singleProcessReportDefinitionDto = new SingleProcessReportDefinitionDto();
    singleProcessReportDefinitionDto.setCollectionId(collectionId);
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest(singleProcessReportDefinitionDto)
      .execute(IdDto.class, HttpStatus.SC_OK)
      .getId();
  }

  private String addEmptyCollectionToOptimize() {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateCollectionRequest()
      .execute(IdDto.class, HttpStatus.SC_OK)
      .getId();
  }

  private Response addSingleReportToCombinedReport(final String combinedReportId, final String reportId) {
    final CombinedReportDefinitionDto combinedReportData = new CombinedReportDefinitionDto();
    combinedReportData.getData().getReports().add(new CombinedReportItemDto(reportId, "red"));
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdateCombinedProcessReportRequest(combinedReportId, combinedReportData)
      .execute();
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  protected static class CombinedReportUpdateData {
    String singleReportId;
    String collectionId;
  }
}
