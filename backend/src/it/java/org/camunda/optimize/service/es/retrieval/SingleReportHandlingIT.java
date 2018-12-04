package org.camunda.optimize.service.es.retrieval;

import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ExecutedFlowNodeFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.VariableFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.variable.BooleanVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ExecutedFlowNodeFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.parameters.ProcessPartDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.util.DateUtilHelper;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.action.get.GetResponse;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.camunda.optimize.test.it.rule.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.test.util.ReportDataBuilderHelper.createReportDataViewRawAsTable;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;


public class SingleReportHandlingIT {

  private static final String FOO_PROCESS_DEFINITION_KEY = "fooProcessDefinitionKey";
  private static final String FOO_PROCESS_DEFINITION_VERSION = "1";
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
          elasticSearchRule.getOptimizeIndex(ElasticsearchConstants.SINGLE_REPORT_TYPE),
          ElasticsearchConstants.SINGLE_REPORT_TYPE,
          id
        )
        .get();

    assertThat(response.isExists(), is(true));
  }

  @Test
  public void writeAndThenReadGivesTheSameResult() {
    // given
    String id = createNewReport();

    // when
    List<SingleReportDefinitionDto> reports = getAllReports();

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
    List<SingleReportDefinitionDto> reports = getAllReports();

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
    List<SingleReportDefinitionDto> reports = getAllReports();

    // then
    assertThat(reports, is(notNullValue()));
    assertThat(reports.isEmpty(), is(true));
  }

  @Test
  public void updateReport() {
    // given
    String id = createNewReport();
    ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setProcessDefinitionKey("procdef");
    reportData.setProcessDefinitionVersion("123");
    reportData.setFilter(Collections.emptyList());
    reportData.setConfiguration("aRandomConfiguration");
    ProcessPartDto processPartDto = new ProcessPartDto();
    processPartDto.setStart("start123");
    processPartDto.setEnd("end123");
    reportData.getParameters().setProcessPart(processPartDto);
    SingleReportDefinitionDto<ProcessReportDataDto> report = new SingleReportDefinitionDto<>();
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
    List<SingleReportDefinitionDto> reports = getAllReports();

    // then
    assertThat(reports.size(), is(1));
    SingleReportDefinitionDto<ProcessReportDataDto> newReport = (SingleReportDefinitionDto<ProcessReportDataDto>) reports.get(0);
    assertThat(newReport.getData().getProcessDefinitionKey(), is("procdef"));
    assertThat(newReport.getData().getProcessDefinitionVersion(), is("123"));
    assertThat(newReport.getData().getConfiguration(), is("aRandomConfiguration"));
    assertThat(newReport.getData().getParameters().getProcessPart().getStart(), is("start123"));
    assertThat(newReport.getData().getParameters().getProcessPart().getEnd(), is("end123"));
    assertThat(newReport.getId(), is(id));
    assertThat(newReport.getCreated(), is(not(shouldBeIgnoredDate)));
    assertThat(newReport.getLastModified(), is(not(shouldBeIgnoredDate)));
    assertThat(newReport.getName(), is("MyReport"));
    assertThat(newReport.getOwner(), is("NewOwner"));
  }

  @Test
  public void updateReportWithoutPDInformation() {
    // given
    String id = createNewReport();
    SingleReportDefinitionDto<ProcessReportDataDto> updatedReport = new SingleReportDefinitionDto<>();
    updatedReport.setData(new ProcessReportDataDto());

    //when
    Response updateReportResponse = getUpdateReportResponse(id, updatedReport);

    //then
    assertThat(updateReportResponse.getStatus(), is(204));

    //when
    ProcessReportDataDto data = new ProcessReportDataDto();
    data.setProcessDefinitionVersion("BLAH");
    updatedReport.setData(data);
    updateReportResponse = getUpdateReportResponse(id, updatedReport);

    //then
    assertThat(updateReportResponse.getStatus(), is(204));

    //when
    data = new ProcessReportDataDto();
    data.setProcessDefinitionKey("BLAH");
    updatedReport.setData(data);
    updateReportResponse = getUpdateReportResponse(id, updatedReport);

    //then
    assertThat(updateReportResponse.getStatus(), is(204));

    //when
    data = new ProcessReportDataDto();
    data.setProcessDefinitionKey("BLAH");
    data.setProcessDefinitionVersion("BLAH");
    updatedReport.setData(data);
    updateReportResponse = getUpdateReportResponse(id, updatedReport);

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

    reportData.getFilter().addAll(DateUtilHelper.createFixedStartDateFilter(null, null));
    reportData.getFilter().addAll(createVariableFilter());
    reportData.getFilter().addAll(createExecutedFlowNodeFilter());
    SingleReportDefinitionDto<ProcessReportDataDto> report = new SingleReportDefinitionDto<>();
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
    List<SingleReportDefinitionDto> reports = getAllReports();

    // then
    assertThat(reports.size(), is(1));
    SingleReportDefinitionDto<ProcessReportDataDto> newReport = (SingleReportDefinitionDto<ProcessReportDataDto>) reports.get(0);
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
    List<ExecutedFlowNodeFilterDto> flowNodeFilter = ExecutedFlowNodeFilterBuilder.construct()
          .id("task1")
          .build();
    return new ArrayList<>(flowNodeFilter);
  }

  @Test
  public void doNotUpdateNullFieldsInReport() {
    // given
    String id = createNewReport();
    ReportDefinitionDto report = constructReportWithFakePD();

    // when
    updateReport(id, report);
    List<SingleReportDefinitionDto> reports = getAllReports();

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
    ProcessReportDataDto reportData =
      createReportDataViewRawAsTable(FOO_PROCESS_DEFINITION_KEY, FOO_PROCESS_DEFINITION_VERSION);
    SingleReportDefinitionDto<ProcessReportDataDto> report = new SingleReportDefinitionDto<>();
    report.setData(reportData);
    report.setName("name");
    OffsetDateTime now = OffsetDateTime.now();
    report.setOwner("owner");
    updateReport(reportId, report);

    // when
    RawDataProcessReportResultDto result = evaluateRawDataReportById(reportId);

    // then
    assertThat(result.getId(), is(reportId));
    assertThat(result.getName(), is("name"));
    assertThat(result.getOwner(), is("owner"));
    assertThat(result.getCreated().truncatedTo(ChronoUnit.DAYS), is(now.truncatedTo(ChronoUnit.DAYS)));
    assertThat(result.getLastModifier(), is(DEFAULT_USERNAME));
    assertThat(result.getLastModified().truncatedTo(ChronoUnit.DAYS), is(now.truncatedTo(ChronoUnit.DAYS)));
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

    ReportDefinitionDto updatedReport = constructReportWithFakePD();
    updatedReport.setName("B");
    updateReport(id1, updatedReport);
    updatedReport.setName("A");
    updateReport(id2, updatedReport);

    // when
    Map<String, Object> queryParam = new HashMap<>();
    queryParam.put("orderBy", "name");
    List<SingleReportDefinitionDto> reports = getAllReportsWithQueryParam(queryParam);

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
    updateReport(id1, constructReportWithFakePD());

    // when
    Map<String, Object> queryParam = new HashMap<>();
    queryParam.put("orderBy", "lastModified");
    List<SingleReportDefinitionDto> reports = getAllReportsWithQueryParam(queryParam);

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
    updateReport(id1, constructReportWithFakePD());

    // when
    Map<String, Object> queryParam = new HashMap<>();
    queryParam.put("orderBy", "lastModified");
    queryParam.put("sortOrder", "desc");
    List<SingleReportDefinitionDto> reports = getAllReportsWithQueryParam(queryParam);

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
    updateReport(id1, constructReportWithFakePD());

    // when
    Map<String, Object> queryParam = new HashMap<>();
    queryParam.put("resultOffset", 1);
    queryParam.put("orderBy", "lastModified");
    List<SingleReportDefinitionDto> reports = getAllReportsWithQueryParam(queryParam);

    // then
    assertThat(reports.size(), is(2));
    assertThat(reports.get(0).getId(), is(id3));
    assertThat(reports.get(1).getId(), is(id2));
  }

  private ReportDefinitionDto constructReportWithFakePD() {
    SingleReportDefinitionDto<ProcessReportDataDto> reportDefinitionDto = new SingleReportDefinitionDto<>();
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
    updateReport(id1, constructReportWithFakePD());

    // when
    Map<String, Object> queryParam = new HashMap<>();
    queryParam.put("numResults", 2);
    queryParam.put("orderBy", "lastModified");
    List<SingleReportDefinitionDto> reports = getAllReportsWithQueryParam(queryParam);

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
    updateReport(id1, constructReportWithFakePD());

    // when
    Map<String, Object> queryParam = new HashMap<>();
    queryParam.put("numResults", 1);
    queryParam.put("orderBy", "lastModified");
    queryParam.put("reverseOrder", true);
    queryParam.put("resultOffset", 1);
    List<SingleReportDefinitionDto> reports = getAllReportsWithQueryParam(queryParam);

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
            .buildCreateSingleReportRequest()
            .execute(IdDto.class, 200)
            .getId();
  }

  private void updateReport(String id, ReportDefinitionDto updatedReport) {
    Response response = getUpdateReportResponse(id, updatedReport);
    assertThat(response.getStatus(), is(204));
  }

  private Response getUpdateReportResponse(String id, ReportDefinitionDto updatedReport) {
    return embeddedOptimizeRule
            .getRequestExecutor()
            .buildUpdateReportRequest(id, updatedReport)
            .execute();
  }

  private RawDataProcessReportResultDto evaluateRawDataReportById(String reportId) {
    return embeddedOptimizeRule
            .getRequestExecutor()
            .buildEvaluateSavedReportRequest(reportId)
            .execute(RawDataProcessReportResultDto.class, 200);
  }

  private List<SingleReportDefinitionDto> getAllReports() {
    return getAllReportsWithQueryParam(new HashMap<>());
  }

  private List<SingleReportDefinitionDto> getAllReportsWithQueryParam(Map<String, Object> queryParams) {
    return embeddedOptimizeRule
            .getRequestExecutor()
            .buildGetAllReportsRequest()
            .addQueryParams(queryParams)
            .executeAndReturnList(SingleReportDefinitionDto.class, 200);
  }
}
