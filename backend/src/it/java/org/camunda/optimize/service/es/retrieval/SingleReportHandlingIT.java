/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.retrieval;

import com.fasterxml.jackson.core.type.TypeReference;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.SingleReportConfigurationDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.heatmap_target_value.HeatmapTargetValueEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.TargetValueUnit;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.group.value.DecisionGroupByVariableValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.BooleanVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.VariableFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.process_part.ProcessPartDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResultDto;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.test.util.ProcessReportDataBuilder;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataType;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.camunda.optimize.test.it.rule.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_PROCESS_REPORT_INDEX_NAME;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.IsNull.notNullValue;


public class SingleReportHandlingIT {

  private static final String FOO_PROCESS_DEFINITION_KEY = "fooProcessDefinitionKey";
  private static final String FOO_PROCESS_DEFINITION_VERSION = "1";

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(engineRule)
    .around(elasticSearchRule)
    .around(embeddedOptimizeRule);

  @After
  public void cleanUp() {
    LocalDateUtil.reset();
  }

  @Test
  public void reportIsWrittenToElasticsearch() throws IOException {
    // given
    String id = createNewReport();

    // when
    GetRequest getRequest = new GetRequest(SINGLE_PROCESS_REPORT_INDEX_NAME, SINGLE_PROCESS_REPORT_INDEX_NAME, id);
    GetResponse getResponse = elasticSearchRule.getOptimizeElasticClient().get(getRequest, RequestOptions.DEFAULT);

    // then
    assertThat(getResponse.isExists(), is(true));
    SingleProcessReportDefinitionDto definitionDto = elasticSearchRule.getObjectMapper()
      .readValue(getResponse.getSourceAsString(), SingleProcessReportDefinitionDto.class);
    assertThat(definitionDto.getData(), notNullValue());
    ProcessReportDataDto data = definitionDto.getData();
    assertThat(data.getFilter(), notNullValue());
    assertThat(data.getConfiguration(), notNullValue());
    assertThat(data.getConfiguration(), equalTo(new SingleReportConfigurationDto()));
    assertThat(
      data.getConfiguration().getColor(),
      is(ReportConstants.DEFAULT_CONFIGURATION_COLOR)
    );
  }

  @Test
  public void writeAndThenReadGivesTheSameResult() {
    // given
    String id = createNewReport();

    // when
    List<ReportDefinitionDto> reports = getAllReports();

    // then
    assertThat(reports, is(notNullValue()));
    assertThat(reports.size(), is(1));
    assertThat(reports.get(0).getId(), is(id));
  }

  @Test
  public void createAndGetSeveralReports() {
    // given
    String id = createNewReport();
    String id2 = createNewReport();
    Set<String> ids = new HashSet<>();
    ids.add(id);
    ids.add(id2);

    // when
    List<ReportDefinitionDto> reports = getAllReports();

    // then
    assertThat(reports, is(notNullValue()));
    assertThat(reports.size(), is(2));
    String reportId1 = reports.get(0).getId();
    String reportId2 = reports.get(1).getId();
    assertThat(ids.contains(reportId1), is(true));
    ids.remove(reportId1);
    assertThat(ids.contains(reportId2), is(true));
  }

  @Test
  public void noReportAvailableReturnsEmptyList() {

    // when
    List<ReportDefinitionDto> reports = getAllReports();

    // then
    assertThat(reports, is(notNullValue()));
    assertThat(reports.isEmpty(), is(true));
  }

  @Test
  public void testUpdateProcessReport() {
    // given
    final String shouldNotBeUpdatedString = "shouldNotBeUpdated";
    String id = createNewReport();
    ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setProcessDefinitionKey("procdef");
    reportData.setProcessDefinitionVersion("123");
    reportData.setFilter(Collections.emptyList());
    SingleReportConfigurationDto configuration = new SingleReportConfigurationDto();
    configuration.setYLabel("fooYLabel");
    reportData.setConfiguration(configuration);
    ProcessPartDto processPartDto = new ProcessPartDto();
    processPartDto.setStart("start123");
    processPartDto.setEnd("end123");
    reportData.getConfiguration().setProcessPart(processPartDto);
    SingleProcessReportDefinitionDto report = new SingleProcessReportDefinitionDto();
    report.setData(reportData);
    report.setId(shouldNotBeUpdatedString);
    report.setLastModifier("shouldNotBeUpdatedManually");
    report.setName("MyReport");
    OffsetDateTime shouldBeIgnoredDate = OffsetDateTime.now().plusHours(1);
    report.setCreated(shouldBeIgnoredDate);
    report.setLastModified(shouldBeIgnoredDate);
    report.setOwner(shouldNotBeUpdatedString);

    // when
    updateSingleProcessReport(id, report);
    List<ReportDefinitionDto> reports = getAllReports();

    // then
    assertThat(reports.size(), is(1));
    SingleProcessReportDefinitionDto newReport = (SingleProcessReportDefinitionDto) reports.get(0);
    assertThat(newReport.getData().getProcessDefinitionKey(), is("procdef"));
    assertThat(newReport.getData().getDefinitionVersions(), contains("123"));
    assertThat(newReport.getData().getConfiguration().getYLabel(), is("fooYLabel"));
    assertThat(newReport.getData().getConfiguration().getProcessPart(), not(Optional.empty()));
    assertThat(newReport.getData().getConfiguration().getProcessPart().get().getStart(), is("start123"));
    assertThat(newReport.getData().getConfiguration().getProcessPart().get().getEnd(), is("end123"));
    assertThat(newReport.getId(), is(id));
    assertThat(newReport.getCreated(), is(not(shouldBeIgnoredDate)));
    assertThat(newReport.getLastModified(), is(not(shouldBeIgnoredDate)));
    assertThat(newReport.getName(), is("MyReport"));
    assertThat(newReport.getOwner(), is(DEFAULT_USERNAME));
  }

  @Test
  public void testUpdateProcessReportRemoveHeatMapTargetValue() {
    // given
    final String id = createNewReport();
    final ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setProcessDefinitionKey("procdef");
    reportData.setProcessDefinitionVersion("123");
    reportData.setFilter(Collections.emptyList());
    final SingleReportConfigurationDto configuration = new SingleReportConfigurationDto();
    configuration.getHeatmapTargetValue().getValues()
      .put("flowNodeId", new HeatmapTargetValueEntryDto(TargetValueUnit.DAYS, "55"));
    reportData.setConfiguration(configuration);

    final SingleProcessReportDefinitionDto report = new SingleProcessReportDefinitionDto();
    report.setData(reportData);
    report.setId("shouldNotBeUpdated");
    report.setLastModifier("shouldNotBeUpdatedManually");
    report.setName("MyReport");
    report.setOwner("NewOwner");
    updateSingleProcessReport(id, report);

    // when
    configuration.getHeatmapTargetValue().setValues(new HashMap<>());
    updateSingleProcessReport(id, report);
    final List<ReportDefinitionDto> reports = getAllReports();

    // then
    assertThat(reports.size(), is(1));
    SingleProcessReportDefinitionDto newReport = (SingleProcessReportDefinitionDto) reports.get(0);
    assertThat(newReport.getData().getConfiguration().getHeatmapTargetValue().getValues().size(), is(0));
  }

  @Test
  public void testUpdateDecisionReportWithGroupByInputVariableName() {
    // given
    String id = embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateSingleDecisionReportRequest()
      .execute(IdDto.class, 200)
      .getId();

    final String variableName = "variableName";
    DecisionReportDataDto expectedReportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey("ID")
      .setDecisionDefinitionVersion("1")
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_INPUT_VARIABLE)
      .setVariableId("id")
      .setVariableName(variableName)
      .build();

    SingleDecisionReportDefinitionDto report = new SingleDecisionReportDefinitionDto();
    report.setData(expectedReportData);
    updateSingleDecisionReport(id, report);

    // when
    updateSingleDecisionReport(id, report);
    List<ReportDefinitionDto> reports = getAllReports();

    // then
    assertThat(reports.size(), is(1));
    SingleDecisionReportDefinitionDto reportFromApi = (SingleDecisionReportDefinitionDto) reports.get(0);
    final DecisionGroupByVariableValueDto value = (DecisionGroupByVariableValueDto)
      reportFromApi.getData().getGroupBy().getValue();
    assertThat(value.getName().isPresent(), is(true));
    assertThat(value.getName().get(), is(variableName));
  }

  @Test
  public void updateReportWithoutPDInformation() {
    // given
    String id = createNewReport();
    SingleProcessReportDefinitionDto updatedReport = new SingleProcessReportDefinitionDto();
    updatedReport.setData(new ProcessReportDataDto());

    //when
    Response updateReportResponse = getUpdateSingleProcessReportResponse(id, updatedReport);

    //then
    assertThat(updateReportResponse.getStatus(), is(204));

    //when
    ProcessReportDataDto data = new ProcessReportDataDto();
    data.setProcessDefinitionVersion("BLAH");
    updatedReport.setData(data);
    updateReportResponse = getUpdateSingleProcessReportResponse(id, updatedReport);

    //then
    assertThat(updateReportResponse.getStatus(), is(204));

    //when
    data = new ProcessReportDataDto();
    data.setProcessDefinitionKey("BLAH");
    updatedReport.setData(data);
    updateReportResponse = getUpdateSingleProcessReportResponse(id, updatedReport);

    //then
    assertThat(updateReportResponse.getStatus(), is(204));

    //when
    data = new ProcessReportDataDto();
    data.setProcessDefinitionKey("BLAH");
    data.setProcessDefinitionVersion("BLAH");
    updatedReport.setData(data);
    updateReportResponse = getUpdateSingleProcessReportResponse(id, updatedReport);

    //then
    assertThat(updateReportResponse.getStatus(), is(204));
  }

  @Test
  public void updateReportWithFilters() {
    // given
    String id = createNewReport();
    ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setProcessDefinitionKey("procdef");
    reportData.setProcessDefinitionVersion("123");

    reportData.getFilter()
      .addAll(ProcessFilterBuilder.filter().fixedStartDate().start(null).end(null).add().buildList());
    reportData.getFilter().addAll(createVariableFilter());
    reportData.getFilter().addAll(createExecutedFlowNodeFilter());
    SingleProcessReportDefinitionDto report = new SingleProcessReportDefinitionDto();
    report.setData(reportData);
    report.setId("shouldNotBeUpdated");
    report.setLastModifier("shouldNotBeUpdatedManually");
    report.setName("MyReport");
    OffsetDateTime shouldBeIgnoredDate = OffsetDateTime.now().plusHours(1);
    report.setCreated(shouldBeIgnoredDate);
    report.setLastModified(shouldBeIgnoredDate);
    report.setOwner("NewOwner");

    // when
    updateSingleProcessReport(id, report);
    List<ReportDefinitionDto> reports = getAllReports();

    // then
    assertThat(reports.size(), is(1));
    SingleProcessReportDefinitionDto newReport =
      (SingleProcessReportDefinitionDto) reports.get(0);
    assertThat(newReport.getData(), is(notNullValue()));
    reportData = newReport.getData();
    assertThat(reportData.getFilter().size(), is(3));
  }

  private List<ProcessFilterDto> createVariableFilter() {
    BooleanVariableFilterDataDto data = new BooleanVariableFilterDataDto("true");
    data.setName("foo");

    VariableFilterDto variableFilterDto = new VariableFilterDto();
    variableFilterDto.setData(data);
    return Collections.singletonList(variableFilterDto);
  }

  private List<ProcessFilterDto> createExecutedFlowNodeFilter() {
    List<ProcessFilterDto> flowNodeFilter = ProcessFilterBuilder
      .filter()
      .executedFlowNodes()
      .id("task1")
      .add()
      .buildList();
    return new ArrayList<>(flowNodeFilter);
  }

  @Test
  public void doNotUpdateNullFieldsInReport() {
    // given
    String id = createNewReport();
    SingleProcessReportDefinitionDto report = constructSingleProcessReportWithFakePD();

    // when
    updateSingleProcessReport(id, report);
    List<ReportDefinitionDto> reports = getAllReports();

    // then
    assertThat(reports.size(), is(1));
    ReportDefinitionDto newDashboard = reports.get(0);
    assertThat(newDashboard.getId(), is(id));
    assertThat(newDashboard.getCreated(), is(notNullValue()));
    assertThat(newDashboard.getLastModified(), is(notNullValue()));
    assertThat(newDashboard.getLastModifier(), is(notNullValue()));
    assertThat(newDashboard.getName(), is(notNullValue()));
    assertThat(newDashboard.getOwner(), is(notNullValue()));
  }

  @Test
  public void reportEvaluationReturnsMetaData() {
    // given
    String reportId = createNewReport();
    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(FOO_PROCESS_DEFINITION_KEY)
      .setProcessDefinitionVersion(FOO_PROCESS_DEFINITION_VERSION)
      .setReportDataType(ProcessReportDataType.RAW_DATA)
      .build();
    SingleProcessReportDefinitionDto report = new SingleProcessReportDefinitionDto();
    report.setData(reportData);
    report.setName("name");
    OffsetDateTime now = OffsetDateTime.now();
    updateSingleProcessReport(reportId, report);

    // when
    AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto> result = evaluateRawDataReportById(reportId);

    // then
    final SingleProcessReportDefinitionDto reportDefinition = result.getReportDefinition();
    assertThat(reportDefinition.getId(), is(reportId));
    assertThat(reportDefinition.getName(), is("name"));
    assertThat(reportDefinition.getOwner(), is(DEFAULT_USERNAME));
    assertThat(reportDefinition.getCreated().truncatedTo(ChronoUnit.DAYS), is(now.truncatedTo(ChronoUnit.DAYS)));
    assertThat(reportDefinition.getLastModifier(), is(DEFAULT_USERNAME));
    assertThat(reportDefinition.getLastModified().truncatedTo(ChronoUnit.DAYS), is(now.truncatedTo(ChronoUnit.DAYS)));
  }

  @Test
  public void evaluateReportWithoutVisualization() {
    // given
    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey("foo")
      .setProcessDefinitionVersion("1")
      .setReportDataType(ProcessReportDataType.PROC_INST_DUR_GROUP_BY_NONE)
      .build();
    reportData.setVisualization(null);

    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      .execute();

    // then
    assertThat(response.getStatus(), is(200));
  }

  @Test
  public void resultListIsSortedByName() {
    // given
    String id1 = createNewReport();
    shiftTimeByOneSecond();
    String id2 = createNewReport();
    shiftTimeByOneSecond();
    String id3 = createNewReport();
    shiftTimeByOneSecond();

    SingleProcessReportDefinitionDto updatedReport = constructSingleProcessReportWithFakePD();
    updatedReport.setName("B");
    updateSingleProcessReport(id1, updatedReport);
    updatedReport.setName("A");
    updateSingleProcessReport(id2, updatedReport);

    // when
    Map<String, Object> queryParam = new HashMap<>();
    queryParam.put("orderBy", "name");
    List<ReportDefinitionDto> reports = getAllReportsWithQueryParam(queryParam);

    // then
    assertThat(reports.size(), is(3));
    assertThat(reports.get(0).getId(), is(id2));
    assertThat(reports.get(1).getId(), is(id1));
    assertThat(reports.get(2).getId(), is(id3));

    // when
    queryParam.put("sortOrder", "desc");
    reports = getAllReportsWithQueryParam(queryParam);

    // then
    assertThat(reports.size(), is(3));
    assertThat(reports.get(0).getId(), is(id3));
    assertThat(reports.get(1).getId(), is(id1));
    assertThat(reports.get(2).getId(), is(id2));
  }

  @Test
  public void resultListIsSortedByLastModified() {
    // given
    String id1 = createNewReport();
    shiftTimeByOneSecond();
    String id2 = createNewReport();
    shiftTimeByOneSecond();
    String id3 = createNewReport();
    shiftTimeByOneSecond();
    updateSingleProcessReport(id1, constructSingleProcessReportWithFakePD());

    // when
    Map<String, Object> queryParam = new HashMap<>();
    queryParam.put("orderBy", "lastModified");
    List<ReportDefinitionDto> reports = getAllReportsWithQueryParam(queryParam);

    // then
    assertThat(reports.size(), is(3));
    assertThat(reports.get(0).getId(), is(id1));
    assertThat(reports.get(1).getId(), is(id3));
    assertThat(reports.get(2).getId(), is(id2));

    //when
    queryParam.put("sortOrder", "desc");
    reports = getAllReportsWithQueryParam(queryParam);
    // then
    assertThat(reports.size(), is(3));
    assertThat(reports.get(0).getId(), is(id2));
    assertThat(reports.get(1).getId(), is(id3));
    assertThat(reports.get(2).getId(), is(id1));
  }

  @Test
  public void resultListIsReversed() {
    // given
    String id1 = createNewReport();
    shiftTimeByOneSecond();
    String id2 = createNewReport();
    shiftTimeByOneSecond();
    String id3 = createNewReport();
    shiftTimeByOneSecond();
    updateSingleProcessReport(id1, constructSingleProcessReportWithFakePD());

    // when
    Map<String, Object> queryParam = new HashMap<>();
    queryParam.put("orderBy", "lastModified");
    queryParam.put("sortOrder", "desc");
    List<ReportDefinitionDto> reports = getAllReportsWithQueryParam(queryParam);

    // then
    assertThat(reports.size(), is(3));
    assertThat(reports.get(2).getId(), is(id1));
    assertThat(reports.get(1).getId(), is(id3));
    assertThat(reports.get(0).getId(), is(id2));
  }

  @Test
  public void resultListIsCutByAnOffset() {
    // given
    String id1 = createNewReport();
    shiftTimeByOneSecond();
    String id2 = createNewReport();
    shiftTimeByOneSecond();
    String id3 = createNewReport();
    shiftTimeByOneSecond();
    updateSingleProcessReport(id1, constructSingleProcessReportWithFakePD());

    // when
    Map<String, Object> queryParam = new HashMap<>();
    queryParam.put("resultOffset", 1);
    queryParam.put("orderBy", "lastModified");
    List<ReportDefinitionDto> reports = getAllReportsWithQueryParam(queryParam);

    // then
    assertThat(reports.size(), is(2));
    assertThat(reports.get(0).getId(), is(id3));
    assertThat(reports.get(1).getId(), is(id2));
  }

  private SingleProcessReportDefinitionDto constructSingleProcessReportWithFakePD() {
    SingleProcessReportDefinitionDto reportDefinitionDto = new SingleProcessReportDefinitionDto();
    ProcessReportDataDto data = new ProcessReportDataDto();
    data.setProcessDefinitionVersion("FAKE");
    data.setProcessDefinitionKey("FAKE");
    reportDefinitionDto.setData(data);
    return reportDefinitionDto;
  }

  @Test
  public void resultListIsCutByMaxResults() {
    // given
    String id1 = createNewReport();
    shiftTimeByOneSecond();
    createNewReport();
    shiftTimeByOneSecond();
    String id3 = createNewReport();
    shiftTimeByOneSecond();
    updateSingleProcessReport(id1, constructSingleProcessReportWithFakePD());

    // when
    Map<String, Object> queryParam = new HashMap<>();
    queryParam.put("numResults", 2);
    queryParam.put("orderBy", "lastModified");
    List<ReportDefinitionDto> reports = getAllReportsWithQueryParam(queryParam);

    // then
    assertThat(reports.size(), is(2));
    assertThat(reports.get(0).getId(), is(id1));
    assertThat(reports.get(1).getId(), is(id3));
  }

  @Test
  public void combineAllResultListQueryParameterRestrictions() {
    // given
    String id1 = createNewReport();
    shiftTimeByOneSecond();
    createNewReport();
    shiftTimeByOneSecond();
    String id3 = createNewReport();
    shiftTimeByOneSecond();
    updateSingleProcessReport(id1, constructSingleProcessReportWithFakePD());

    // when
    Map<String, Object> queryParam = new HashMap<>();
    queryParam.put("numResults", 1);
    queryParam.put("orderBy", "lastModified");
    queryParam.put("reverseOrder", true);
    queryParam.put("resultOffset", 1);
    List<ReportDefinitionDto> reports = getAllReportsWithQueryParam(queryParam);

    // then
    assertThat(reports.size(), is(1));
    assertThat(reports.get(0).getId(), is(id3));
  }

  private void shiftTimeByOneSecond() {
    LocalDateUtil.setCurrentTime(LocalDateUtil.getCurrentDateTime().plusSeconds(1L));
  }

  private String createNewReport() {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest()
      .execute(IdDto.class, 200)
      .getId();
  }

  private void updateSingleProcessReport(String id, SingleProcessReportDefinitionDto updatedReport) {
    Response response = getUpdateSingleProcessReportResponse(id, updatedReport);
    assertThat(response.getStatus(), is(204));
  }

  private void updateSingleDecisionReport(String id, SingleDecisionReportDefinitionDto updatedReport) {
    Response response = getUpdateSingleDecisionReportResponse(id, updatedReport);
    assertThat(response.getStatus(), is(204));
  }

  private Response getUpdateSingleProcessReportResponse(String id, SingleProcessReportDefinitionDto updatedReport) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildUpdateSingleProcessReportRequest(id, updatedReport)
      .execute();
  }

  private Response getUpdateSingleDecisionReportResponse(String id, SingleDecisionReportDefinitionDto updatedReport) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildUpdateSingleDecisionReportRequest(id, updatedReport)
      .execute();
  }

  private AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluateRawDataReportById(String reportId) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateSavedReportRequest(reportId)
      // @formatter:off
      .execute(new TypeReference<AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto>>() {});
    // @formatter:on
  }

  private List<ReportDefinitionDto> getAllReports() {
    return getAllReportsWithQueryParam(new HashMap<>());
  }

  private List<ReportDefinitionDto> getAllReportsWithQueryParam(Map<String, Object> queryParams) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetAllReportsRequest()
      .addQueryParams(queryParams)
      .executeAndReturnList(ReportDefinitionDto.class, 200);
  }
}
