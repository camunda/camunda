package org.camunda.optimize.service.es.retrieval;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedMapReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.MapSingleReportResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.schema.type.CombinedReportType;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.action.get.GetResponse;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.report.command.util.ReportConstants.COMBINED_REPORT_TYPE;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.GROUP_BY_START_DATE_TYPE;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.TABLE_VISUALIZATION;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_PROCESS_INSTANCE_ENTITY;
import static org.camunda.optimize.test.it.rule.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.test.util.ReportDataHelper.createCombinedReport;
import static org.camunda.optimize.test.util.ReportDataHelper.createCountFlowNodeFrequencyGroupByFlowNode;
import static org.camunda.optimize.test.util.ReportDataHelper.createPiFrequencyCountGroupedByNone;
import static org.camunda.optimize.test.util.ReportDataHelper.createReportDataViewRawAsTable;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.*;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;


public class CombinedReportHandlingIT {

  private static final String START_EVENT = "startEvent";
  private static final String END_EVENT = "endEvent";
  private static final String SERVICE_TASK_ID = "aSimpleServiceTask";

  private static final String FOO_PROCESS_DEFINITION_KEY = "fooProcessDefinitionKey";
  private static final String FOO_PROCESS_DEFINITION_VERSION = "1";
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
  public void reportIsWrittenToElasticsearch() {
    // given
    String id = createNewCombinedReport();

    // then
    GetResponse response =
      elasticSearchRule.getClient()
        .prepareGet(
          elasticSearchRule.getOptimizeIndex(ElasticsearchConstants.COMBINED_REPORT_TYPE),
          ElasticsearchConstants.COMBINED_REPORT_TYPE,
          id
        )
        .get();

    assertThat(response.isExists(), is(true));
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
    String id = createNewCombinedReport();
    CombinedReportDefinitionDto report = new CombinedReportDefinitionDto();
    report.setData(createCombinedReport("foo123"));
    report.setId("shouldNotBeUpdated");
    report.setLastModifier("shouldNotBeUpdatedManually");
    report.setName("MyReport");
    OffsetDateTime shouldBeIgnoredDate = OffsetDateTime.now().plusHours(1);
    report.setCreated(shouldBeIgnoredDate);
    report.setLastModified(shouldBeIgnoredDate);
    report.setOwner("NewOwner");
    report.setReportType(COMBINED_REPORT_TYPE);

    // when
    updateReport(id, report);
    List<ReportDefinitionDto> reports = getAllReports();

    // then
    assertThat(reports.size(), is(1));
    CombinedReportDefinitionDto newReport = (CombinedReportDefinitionDto) reports.get(0);
    assertThat(newReport.getData().getReportIds().isEmpty(), is(false));
    assertThat(newReport.getData().getReportIds().get(0), is("foo123"));
    assertThat(newReport.getData().getConfiguration(), is("aRandomConfiguration"));
    assertThat(newReport.getId(), is(id));
    assertThat(newReport.getCreated(), is(not(shouldBeIgnoredDate)));
    assertThat(newReport.getLastModified(), is(not(shouldBeIgnoredDate)));
    assertThat(newReport.getName(), is("MyReport"));
    assertThat(newReport.getOwner(), is("NewOwner"));
    assertThat(newReport.getReportType(), is(COMBINED_REPORT_TYPE));
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
    CombinedMapReportResultDto result = evaluateCombinedReportById(reportId);

    // then
    assertThat(result.getId(), is(reportId));
    assertThat(result.getName(), is("name"));
    assertThat(result.getOwner(), is("owner"));
    assertThat(result.getCreated().truncatedTo(ChronoUnit.DAYS), is(now.truncatedTo(ChronoUnit.DAYS)));
    assertThat(result.getLastModifier(), is(DEFAULT_USERNAME));
    assertThat(result.getLastModified().truncatedTo(ChronoUnit.DAYS), is(now.truncatedTo(ChronoUnit.DAYS)));
    assertThat(result.getData(), is(notNullValue()));
    CombinedReportDataDto dataDto = result.getData();
    assertThat(dataDto.getReportIds().size(), is(1));
    assertThat(dataDto.getReportIds().get(0), is(singleReportId));
    assertThat(dataDto.getConfiguration(), is("aRandomConfiguration"));
  }

  @Test
  public void deleteCombinedReport() {
    // given
    String reportId = createNewCombinedReport();
    CombinedReportDefinitionDto report = new CombinedReportDefinitionDto();
    CombinedReportDataDto dataDto = new CombinedReportDataDto();
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
  public void canCombineReports() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String singleReportId = createNewSingleMapReport(engineDto);
    String singleReportId2 = createNewSingleMapReport(engineDto);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    String reportId = createNewCombinedReport(singleReportId, singleReportId2);
    CombinedMapReportResultDto result = evaluateCombinedReportById(reportId);

    // then
    assertThat(result.getId(), is(reportId));
    Map<String, MapSingleReportResultDto> resultMap = result.getResult();
    assertThat(resultMap.size(), is(2));
    Map<String, Long> flowNodeToCount = resultMap.get(singleReportId).getResult();
    assertThat(flowNodeToCount.size(), is(3));
    Map<String, Long> flowNodeToCount2 = resultMap.get(singleReportId2).getResult();
    assertThat(flowNodeToCount.size(), is(3));
  }

  @Test
  public void reportsThatCantBeEvaluatedAreIgnored() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String singleReportId = createNewSingleReport();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    String reportId = createNewCombinedReport(singleReportId);
    CombinedMapReportResultDto result = evaluateCombinedReportById(reportId);

    // then
    assertThat(result.getId(), is(reportId));
    Map<String, MapSingleReportResultDto> resultMap = result.getResult();
    assertThat(resultMap.size(), is(0));
  }

  @Test
  public void deletedSingleReportsAreRemovedFromCombinedReport() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String singleReportIdToDelete = createNewSingleReport();
    String remainingSingleReportId = createNewSingleMapReport(engineDto);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    String combinedReportId = createNewCombinedReport(singleReportIdToDelete, remainingSingleReportId);
    deleteReport(singleReportIdToDelete);
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
      .map(r -> (CombinedReportDefinitionDto)r)
      .findFirst();
    assertThat(combinedReport.isPresent(), is(true));
    CombinedReportDataDto dataDto = combinedReport.get().getData();
    assertThat(dataDto.getReportIds().size(), is(1));
    assertThat(dataDto.getReportIds().get(0), is(remainingSingleReportId));
  }

  @Test
  public void singleReportsAreRemovedFromCombinedReportOnReportUpdateWithVisualizeAsChanged() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    SingleReportDataDto countFlowNodeFrequencyGroupByFlowNode = createCountFlowNodeFrequencyGroupByFlowNode(
      engineDto.getProcessDefinitionKey(),
      engineDto.getProcessDefinitionVersion()
    );
    String singleReportIdToUpdate = createNewSingleMapReport(countFlowNodeFrequencyGroupByFlowNode);
    String remainingSingleReportId = createNewSingleMapReport(engineDto);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    String combinedReportId = createNewCombinedReport(singleReportIdToUpdate, remainingSingleReportId);
    SingleReportDefinitionDto report = new SingleReportDefinitionDto();
    countFlowNodeFrequencyGroupByFlowNode.setVisualization(TABLE_VISUALIZATION);
    report.setData(countFlowNodeFrequencyGroupByFlowNode);
    updateReport(singleReportIdToUpdate, report);
    List<ReportDefinitionDto> reports = getAllReports();

    // then
    Set<String> resultSet = reports.stream()
      .map(ReportDefinitionDto::getId)
      .collect(Collectors.toSet());
    assertThat(resultSet.size(), is(3));
    assertThat(resultSet.contains(combinedReportId), is(true));
    Optional<CombinedReportDefinitionDto> combinedReport = reports.stream()
      .filter(r -> r instanceof CombinedReportDefinitionDto)
      .map(r -> (CombinedReportDefinitionDto)r)
      .findFirst();
    assertThat(combinedReport.isPresent(), is(true));
    CombinedReportDataDto dataDto = combinedReport.get().getData();
    assertThat(dataDto.getReportIds().size(), is(1));
    assertThat(dataDto.getReportIds().get(0), is(remainingSingleReportId));
  }

  @Test
  public void singleReportsAreRemovedFromCombinedReportOnReportUpdateWithGroupByChanged() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    SingleReportDataDto countFlowNodeFrequencyGroupByFlowNode = createCountFlowNodeFrequencyGroupByFlowNode(
      engineDto.getProcessDefinitionKey(),
      engineDto.getProcessDefinitionVersion()
    );
    String singleReportIdToUpdate = createNewSingleMapReport(countFlowNodeFrequencyGroupByFlowNode);
    String remainingSingleReportId = createNewSingleMapReport(engineDto);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    String combinedReportId = createNewCombinedReport(singleReportIdToUpdate, remainingSingleReportId);
    SingleReportDefinitionDto report = new SingleReportDefinitionDto();
    countFlowNodeFrequencyGroupByFlowNode.getGroupBy().setType(GROUP_BY_START_DATE_TYPE);
    report.setData(countFlowNodeFrequencyGroupByFlowNode);
    updateReport(singleReportIdToUpdate, report);
    List<ReportDefinitionDto> reports = getAllReports();

    // then
    Set<String> resultSet = reports.stream()
      .map(ReportDefinitionDto::getId)
      .collect(Collectors.toSet());
    assertThat(resultSet.size(), is(3));
    assertThat(resultSet.contains(combinedReportId), is(true));
    Optional<CombinedReportDefinitionDto> combinedReport = reports.stream()
      .filter(r -> r instanceof CombinedReportDefinitionDto)
      .map(r -> (CombinedReportDefinitionDto)r)
      .findFirst();
    assertThat(combinedReport.isPresent(), is(true));
    CombinedReportDataDto dataDto = combinedReport.get().getData();
    assertThat(dataDto.getReportIds().size(), is(1));
    assertThat(dataDto.getReportIds().get(0), is(remainingSingleReportId));
  }

  @Test
  public void singleReportsAreRemovedFromCombinedReportOnReportUpdateWithViewChanged() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    SingleReportDataDto countFlowNodeFrequencyGroupByFlowNode = createCountFlowNodeFrequencyGroupByFlowNode(
      engineDto.getProcessDefinitionKey(),
      engineDto.getProcessDefinitionVersion()
    );
    String singleReportIdToUpdate = createNewSingleMapReport(countFlowNodeFrequencyGroupByFlowNode);
    String remainingSingleReportId = createNewSingleMapReport(engineDto);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    String combinedReportId = createNewCombinedReport(singleReportIdToUpdate, remainingSingleReportId);
    SingleReportDefinitionDto report = new SingleReportDefinitionDto();
    countFlowNodeFrequencyGroupByFlowNode.getView().setEntity(VIEW_PROCESS_INSTANCE_ENTITY);
    report.setData(countFlowNodeFrequencyGroupByFlowNode);
    updateReport(singleReportIdToUpdate, report);
    List<ReportDefinitionDto> reports = getAllReports();

    // then
    Set<String> resultSet = reports.stream()
      .map(ReportDefinitionDto::getId)
      .collect(Collectors.toSet());
    assertThat(resultSet.size(), is(3));
    assertThat(resultSet.contains(combinedReportId), is(true));
    Optional<CombinedReportDefinitionDto> combinedReport = reports.stream()
      .filter(r -> r instanceof CombinedReportDefinitionDto)
      .map(r -> (CombinedReportDefinitionDto)r)
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
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    CombinedMapReportResultDto result =
      evaluateUnsavedCombined(createCombinedReport(singleReportId, singleReportId2));

    // then
    Map<String, MapSingleReportResultDto> resultMap = result.getResult();
    assertThat(resultMap.size(), is(2));
    Map<String, Long> flowNodeToCount = resultMap.get(singleReportId).getResult();
    assertThat(flowNodeToCount.size(), is(3));
    Map<String, Long> flowNodeToCount2 = resultMap.get(singleReportId2).getResult();
    assertThat(flowNodeToCount.size(), is(3));
  }

  @Test
  public void evaluationResultContainsSingleResultMetaData() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String singleReportId = createNewSingleMapReport(engineDto);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    CombinedMapReportResultDto result =
      evaluateUnsavedCombined(createCombinedReport(singleReportId));

    // then
    Map<String, MapSingleReportResultDto> resultMap = result.getResult();
    assertThat(resultMap.size(), is(1));
    MapSingleReportResultDto mapResult = resultMap.get(singleReportId);
    assertThat(mapResult.getName(), is(TEST_REPORT_NAME));
  }

  @Test
  public void combinedReportWithSingleNumberReport() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String singleReportId = createNewSingleNumberReport(engineDto);
    String singleReportId2 = createNewSingleMapReport(engineDto);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    CombinedMapReportResultDto result =
      evaluateUnsavedCombined(createCombinedReport(singleReportId, singleReportId2));

    // then
    Map<String, MapSingleReportResultDto> resultMap = result.getResult();
    assertThat(resultMap.size(), is(1));
    assertThat(resultMap.containsKey(singleReportId2), is(true));
  }

  @Test
  public void combinedReportWithRawReport() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String singleReportId = createNewSingleRawReport(engineDto);
    String singleReportId2 = createNewSingleMapReport(engineDto);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    CombinedMapReportResultDto result =
      evaluateUnsavedCombined(createCombinedReport(singleReportId, singleReportId2));

    // then
    Map<String, MapSingleReportResultDto> resultMap = result.getResult();
    assertThat(resultMap.size(), is(1));
    assertThat(resultMap.containsKey(singleReportId2), is(true));
  }

  @Test
  public void cantEvaluateCombinedReportWithCombinedReport() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String combinedReportId = createNewCombinedReport();
    String singleReportId2 = createNewSingleMapReport(engineDto);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    CombinedMapReportResultDto result =
      evaluateUnsavedCombined(createCombinedReport(combinedReportId, singleReportId2));

    // then
    Map<String, MapSingleReportResultDto> resultMap = result.getResult();
    assertThat(resultMap.size(), is(1));
    Map<String, Long> flowNodeToCount = resultMap.get(singleReportId2).getResult();
    assertThat(flowNodeToCount.size(), is(3));
  }

  private String createNewSingleMapReport(ProcessInstanceEngineDto engineDto) {
    SingleReportDataDto countFlowNodeFrequencyGroupByFlowNode =
      createCountFlowNodeFrequencyGroupByFlowNode(
        engineDto.getProcessDefinitionKey(),
        engineDto.getProcessDefinitionVersion()
      );
    return createNewSingleMapReport(countFlowNodeFrequencyGroupByFlowNode);
  }

  private String createNewSingleMapReport(SingleReportDataDto data) {
    String singleReportId = createNewSingleReport();
    SingleReportDefinitionDto definitionDto = new SingleReportDefinitionDto();
    definitionDto.setName(TEST_REPORT_NAME);
    definitionDto.setData(data);
    updateReport(singleReportId, definitionDto);
    return singleReportId;
  }


  private String createNewSingleNumberReport(ProcessInstanceEngineDto engineDto) {
    String singleReportId = createNewSingleReport();
    SingleReportDataDto countFlowNodeFrequencyGroupByFlowNode =
      createPiFrequencyCountGroupedByNone(engineDto.getProcessDefinitionKey(), engineDto.getProcessDefinitionVersion());
    SingleReportDefinitionDto definitionDto = new SingleReportDefinitionDto();
    definitionDto.setData(countFlowNodeFrequencyGroupByFlowNode);
    updateReport(singleReportId, definitionDto);
    return singleReportId;
  }

  private String createNewSingleRawReport(ProcessInstanceEngineDto engineDto) {
    String singleReportId = createNewSingleReport();
    SingleReportDataDto countFlowNodeFrequencyGroupByFlowNode =
      createReportDataViewRawAsTable(engineDto.getProcessDefinitionKey(),engineDto.getProcessDefinitionVersion());
    SingleReportDefinitionDto definitionDto = new SingleReportDefinitionDto();
    definitionDto.setData(countFlowNodeFrequencyGroupByFlowNode);
    updateReport(singleReportId, definitionDto);
    return singleReportId;
  }

  private String createNewCombinedReport(String... singleReportIds) {
    String reportId = createNewCombinedReport();
    CombinedReportDefinitionDto report = new CombinedReportDefinitionDto();
    CombinedReportDataDto dataDto = new CombinedReportDataDto();
    report.setData(createCombinedReport(singleReportIds));
    updateReport(reportId, report);
    return reportId;
  }

  private ProcessInstanceEngineDto deploySimpleServiceTaskProcessDefinition() {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("aProcess" )
      .startEvent(START_EVENT)
      .serviceTask(SERVICE_TASK_ID)
        .camundaExpression("${true}")
      .endEvent(END_EVENT)
      .done();
    return engineRule.deployAndStartProcess(modelInstance);
  }


  private void deleteReport(String reportId) {
    Response response = embeddedOptimizeRule
            .getRequestExecutor()
            .buildDeleteReportRequest(reportId)
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

  private CombinedMapReportResultDto evaluateCombinedReportById(String reportId) {
    return embeddedOptimizeRule
            .getRequestExecutor()
            .buildEvaluateSavedReportRequest(reportId)
            .execute(CombinedMapReportResultDto.class, 200);
  }

  private CombinedMapReportResultDto evaluateUnsavedCombined(CombinedReportDataDto reportDataDto) {
    Response response = evaluateUnsavedCombinedReportAndReturnResponse(reportDataDto);

    // then the status code is okay
    assertThat(response.getStatus(), is(200));
    return response.readEntity(CombinedMapReportResultDto.class);
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
