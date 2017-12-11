package org.camunda.optimize.service.es.retrieval;

import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ViewDto;
import org.camunda.optimize.dto.optimize.query.report.filter.DateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.filter.ExecutedFlowNodeFilterDto;
import org.camunda.optimize.dto.optimize.query.report.filter.FilterDto;
import org.camunda.optimize.dto.optimize.query.report.filter.VariableFilterDto;
import org.camunda.optimize.dto.optimize.query.report.filter.data.DateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.filter.data.VariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.filter.util.ExecutedFlowNodeFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.result.ReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.result.raw.RawDataReportResultDto;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.elasticsearch.action.get.GetResponse;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_RAW_DATA_OPERATION;
import static org.camunda.optimize.test.it.rule.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/rest/restTestApplicationContext.xml"})
public class ReportHandlingIT {

  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(embeddedOptimizeRule);

  @After
  public void cleanUp() {
    LocalDateUtil.reset();
  }

  @Test
  public void reportIsWrittenToElasticsearch() {
    // given
    String id = createNewReport();

    // then
    GetResponse response =
      elasticSearchRule.getClient()
        .prepareGet(
          elasticSearchRule.getOptimizeIndex(elasticSearchRule.getReportType()),
          elasticSearchRule.getReportType(),
          id
        )
        .get();

    assertThat(response.isExists(), is(true));
  }

  @Test
  public void writeAndThenReadGivesTheSameResult() throws IOException {
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
  public void createAndGetSeveralReports() throws IOException {
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
  public void noReportAvailableReturnsEmptyList() throws IOException {

    // when
    List<ReportDefinitionDto> reports = getAllReports();

    // then
    assertThat(reports, is(notNullValue()));
    assertThat(reports.isEmpty(), is(true));
  }

  @Test
  public void updateReport() throws Exception {
    // given
    String id = createNewReport();
    ReportDataDto reportData = new ReportDataDto();
    reportData.setProcessDefinitionId("procdef-123");
    reportData.setFilter(Collections.emptyList());
    ReportDefinitionDto report = new ReportDefinitionDto();
    report.setData(reportData);
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
    assertThat(reports.size(), is(1));
    ReportDefinitionDto newReport = reports.get(0);
    assertThat(newReport.getData().getProcessDefinitionId(), is("procdef-123"));
    assertThat(newReport.getId(), is(id));
    assertThat(newReport.getCreated(), is(not(shouldBeIgnoredDate)));
    assertThat(newReport.getLastModified(), is(not(shouldBeIgnoredDate)));
    assertThat(newReport.getName(), is("MyReport"));
    assertThat(newReport.getOwner(), is("NewOwner"));
  }

  @Test
  public void updateReportWithFilters() throws Exception {
    // given
    String id = createNewReport();
    ReportDataDto reportData = new ReportDataDto();
    reportData.setProcessDefinitionId("procdef-123");

    reportData.getFilter().addAll(createDateFilter());
    reportData.getFilter().addAll(createVariableFilter());
    reportData.getFilter().addAll(createExecutedFlowNodeFilter());
    ReportDefinitionDto report = new ReportDefinitionDto();
    report.setData(reportData);
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
    assertThat(reports.size(), is(1));
    ReportDefinitionDto newReport = reports.get(0);
    assertThat(newReport.getData(), is(notNullValue()));
    reportData = newReport.getData();
    assertThat(reportData.getFilter().size(), is(3));
  }

  public List<FilterDto> createDateFilter() {
    DateFilterDataDto date = new DateFilterDataDto();
    date.setOperator("foo");
    date.setType("bar");
    date.setValue(OffsetDateTime.now());

    DateFilterDto dateFilterDto = new DateFilterDto();
    dateFilterDto.setData(date);
    return Collections.singletonList(dateFilterDto);
  }

  private List<FilterDto> createVariableFilter() {
    VariableFilterDataDto data = new VariableFilterDataDto();
    data.setName("foo");
    data.setType("boolean");
    data.setOperator("=");
    data.setValues(Collections.singletonList("true"));

    VariableFilterDto variableFilterDto = new VariableFilterDto();
    variableFilterDto.setData(data);
    return Collections.singletonList(variableFilterDto);
  }

  private List<FilterDto> createExecutedFlowNodeFilter() {
    List<ExecutedFlowNodeFilterDto> flowNodeFilter = ExecutedFlowNodeFilterBuilder.construct()
          .id("task1")
          .build();
    return new ArrayList<>(flowNodeFilter);
  }

  @Test
  public void doNotUpdateNullFieldsInReport() throws Exception {
    // given
    String id = createNewReport();
    ReportDefinitionDto report = new ReportDefinitionDto();

    // when
    updateReport(id, report);
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
  public void reportEvaluationReturnsMetaData() throws Exception {
    // given
    String reportId = createNewReport();
    ReportDataDto reportData = createDefaultReportData();
    ReportDefinitionDto report = new ReportDefinitionDto();
    report.setData(reportData);
    report.setName("name");
    OffsetDateTime now = OffsetDateTime.now();
    report.setOwner("owner");
    updateReport(reportId, report);

    // when
    ReportResultDto result = evaluateRawDataReportById(reportId);

    // then
    assertThat(result.getId(), is(reportId));
    assertThat(result.getName(), is("name"));
    assertThat(result.getOwner(), is("owner"));
    assertThat(result.getCreated().truncatedTo(ChronoUnit.DAYS), is(now.truncatedTo(ChronoUnit.DAYS)));
    assertThat(result.getLastModifier(), is(DEFAULT_USERNAME));
    assertThat(result.getLastModified().truncatedTo(ChronoUnit.DAYS), is(now.truncatedTo(ChronoUnit.DAYS)));
  }

  @Test
  public void resultListIsSortedByLastModified() throws IOException {
    // given
    String id1 = createNewReport();
    shiftTimeByOneSecond();
    String id2 = createNewReport();
    shiftTimeByOneSecond();
    String id3 = createNewReport();
    shiftTimeByOneSecond();
    updateReport(id1, new ReportDefinitionDto());

    // when
    Map<String, Object> queryParam = new HashMap<>();
    queryParam.put("orderBy", "lastModified");
    List<ReportDefinitionDto> reports = getAllReportsWithQueryParam(queryParam);

    // then
    assertThat(reports.size(), is(3));
    assertThat(reports.get(0).getId(), is(id1));
    assertThat(reports.get(1).getId(), is(id3));
    assertThat(reports.get(2).getId(), is(id2));
  }

  @Test
  public void resultListIsReversed() throws Exception {
    // given
    String id1 = createNewReport();
    shiftTimeByOneSecond();
    String id2 = createNewReport();
    shiftTimeByOneSecond();
    String id3 = createNewReport();
    shiftTimeByOneSecond();
    updateReport(id1, new ReportDefinitionDto());

    // when
    Map<String, Object> queryParam = new HashMap<>();
    queryParam.put("orderBy", "lastModified");
    queryParam.put("reverseOrder", true);
    List<ReportDefinitionDto> reports = getAllReportsWithQueryParam(queryParam);

    // then
    assertThat(reports.size(), is(3));
    assertThat(reports.get(2).getId(), is(id1));
    assertThat(reports.get(1).getId(), is(id3));
    assertThat(reports.get(0).getId(), is(id2));
  }

  @Test
  public void resultListIsCutByAnOffset() throws Exception {
    // given
    String id1 = createNewReport();
    shiftTimeByOneSecond();
    String id2 = createNewReport();
    shiftTimeByOneSecond();
    String id3 = createNewReport();
    shiftTimeByOneSecond();
    updateReport(id1, new ReportDefinitionDto());

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

  @Test
  public void resultListIsCutByMaxResults() throws Exception {
    // given
    String id1 = createNewReport();
    shiftTimeByOneSecond();
    createNewReport();
    shiftTimeByOneSecond();
    String id3 = createNewReport();
    shiftTimeByOneSecond();
    updateReport(id1, new ReportDefinitionDto());

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
  public void combineAllResultListQueryParameterRestrictions() throws Exception {
    // given
    String id1 = createNewReport();
    shiftTimeByOneSecond();
    createNewReport();
    shiftTimeByOneSecond();
    String id3 = createNewReport();
    shiftTimeByOneSecond();
    updateReport(id1, new ReportDefinitionDto());

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
    String token = embeddedOptimizeRule.getAuthenticationToken();
    Response response =
      embeddedOptimizeRule.target("report")
        .request()
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .post(Entity.json(""));
    assertThat(response.getStatus(), is(200));

    return response.readEntity(IdDto.class).getId();
  }

  private void updateReport(String id, ReportDefinitionDto updatedReport) {
    String token = embeddedOptimizeRule.getAuthenticationToken();
    Response response =
      embeddedOptimizeRule.target("report/" + id)
        .request()
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .put(Entity.json(updatedReport));
    assertThat(response.getStatus(), is(204));
  }

  private ReportDataDto createDefaultReportData() {
    ReportDataDto reportData = new ReportDataDto();
    reportData.setProcessDefinitionId("fooProcessDefinitionId");
    reportData.setVisualization("table");
    reportData.setView(new ViewDto(VIEW_RAW_DATA_OPERATION));
    return reportData;
  }

  private RawDataReportResultDto evaluateRawDataReportById(String reportId) {
    String token = embeddedOptimizeRule.getAuthenticationToken();
    Response response = embeddedOptimizeRule.target("report/" + reportId + "/evaluate")
      .request()
      .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
      .get();
    assertThat(response.getStatus(), is(200));

    return response.readEntity(RawDataReportResultDto.class);
  }

  private List<ReportDefinitionDto> getAllReports() throws IOException {
    return getAllReportsWithQueryParam(new HashMap<>());
  }

  private List<ReportDefinitionDto> getAllReportsWithQueryParam(Map<String, Object> queryParams) throws IOException {
    String token = embeddedOptimizeRule.getAuthenticationToken();
      WebTarget webTarget = embeddedOptimizeRule.target("report");
    for (Map.Entry<String, Object> queryParam : queryParams.entrySet()) {
      webTarget = webTarget.queryParam(queryParam.getKey(), queryParam.getValue());
    }
    Response response =
      webTarget
        .request()
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .get();

    assertThat(response.getStatus(), is(200));
    return response.readEntity(new GenericType<List<ReportDefinitionDto>>() {
    });
  }
}
