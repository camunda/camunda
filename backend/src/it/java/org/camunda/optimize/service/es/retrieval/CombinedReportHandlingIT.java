package org.camunda.optimize.service.es.retrieval;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.configuration.CombinedReportConfigurationDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.SingleReportConfigurationDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportNumberResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
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
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createPiFrequencyCountGroupedByNone;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createProcessReportDataViewRawAsTable;
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
    report.getData().getConfiguration().setxLabel("FooXLabel");;
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
    CombinedProcessReportResultDto<?> result = evaluateCombinedReportById(reportId);

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
    assertThat(report.getData().getConfiguration(), equalTo(new CombinedReportConfigurationDto()));
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
  public void canCombineReports() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String singleReportId = createNewSingleMapReport(engineDto);
    String singleReportId2 = createNewSingleMapReport(engineDto);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    String reportId = createNewCombinedReport(singleReportId, singleReportId2);
    CombinedProcessReportResultDto<ProcessReportMapResultDto> result = evaluateCombinedReportById(reportId);

    // then
    assertThat(result.getId(), is(reportId));
    Map<String, ProcessReportMapResultDto> resultMap = result.getResult();
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
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    String reportId = createNewCombinedReport(singleReportId);
    CombinedProcessReportResultDto result = evaluateCombinedReportById(reportId);

    // then
    assertThat(result.getId(), is(reportId));
    Map<String, ProcessReportMapResultDto> resultMap = result.getResult();
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
      .map(r -> (CombinedReportDefinitionDto)r)
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
      .map(r -> (CombinedReportDefinitionDto)r)
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
      .map(r -> (CombinedReportDefinitionDto)r)
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
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    CombinedProcessReportResultDto result =
      evaluateUnsavedCombined(createCombinedReport(singleReportId, singleReportId2));

    // then
    Map<String, ProcessReportMapResultDto> resultMap = result.getResult();
    assertThat(resultMap.size(), is(2));
    Map<String, Long> flowNodeToCount = resultMap.get(singleReportId).getResult();
    assertThat(flowNodeToCount.size(), is(3));
    Map<String, Long> flowNodeToCount2 = resultMap.get(singleReportId2).getResult();
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
    CombinedProcessReportResultDto result =
      evaluateUnsavedCombined(createCombinedReport(singleReportId));

    // then
    Map<String, ProcessReportMapResultDto> resultMap = result.getResult();
    assertThat(resultMap.size(), is(1));
    ProcessReportMapResultDto mapResult = resultMap.get(singleReportId);
    assertThat(mapResult.getName(), is(TEST_REPORT_NAME));
  }

  @Test
  public void combinedReportWithSingleNumberReports() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String singleReportId1 = createNewSingleNumberReport(engineDto);
    String singleReportId2 = createNewSingleNumberReport(engineDto);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    CombinedProcessReportResultDto<ProcessReportNumberResultDto> result = evaluateUnsavedCombined(
      createCombinedReport(singleReportId1, singleReportId2)
    );

    // then
    Map<String, ProcessReportNumberResultDto> resultMap = result.getResult();
    assertThat(resultMap.size(), is(2));
    assertThat(resultMap.keySet(), contains(singleReportId1, singleReportId2));
  }

  @Test
  public void combinedReportWithSingleMapReports() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String singleReportId1 = createNewSingleMapReport(engineDto);
    String singleReportId2 = createNewSingleMapReport(engineDto);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    CombinedProcessReportResultDto<ProcessReportMapResultDto> result = evaluateUnsavedCombined(
      createCombinedReport(singleReportId1, singleReportId2)
    );

    // then
    Map<String, ProcessReportMapResultDto> resultMap = result.getResult();
    assertThat(resultMap.size(), is(2));
    assertThat(resultMap.keySet(), contains(singleReportId1, singleReportId2));
  }

  @Test
  public void combinedReportWithSingleNumberAndMapReport_firstWins() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String singleReportId = createNewSingleNumberReport(engineDto);
    String singleReportId2 = createNewSingleMapReport(engineDto);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    CombinedProcessReportResultDto<ProcessReportNumberResultDto> result = evaluateUnsavedCombined(
      createCombinedReport(singleReportId, singleReportId2)
    );

    // then
    Map<String, ProcessReportNumberResultDto> resultMap = result.getResult();
    assertThat(resultMap.size(), is(1));
    assertThat(resultMap.keySet(), contains(singleReportId));
  }

  @Test
  public void combinedReportWithRawReport() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String singleReportId = createNewSingleRawReport(engineDto);
    String singleReportId2 = createNewSingleMapReport(engineDto);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    CombinedProcessReportResultDto result =
      evaluateUnsavedCombined(createCombinedReport(singleReportId, singleReportId2));

    // then
    Map<String, ProcessReportMapResultDto> resultMap = result.getResult();
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

  private String createNewSingleMapReport(ProcessReportDataDto data) {
    String singleReportId = createNewSingleReport();
    SingleProcessReportDefinitionDto definitionDto = new SingleProcessReportDefinitionDto();
    definitionDto.setName(TEST_REPORT_NAME);
    definitionDto.setData(data);
    updateReport(singleReportId, definitionDto);
    return singleReportId;
  }


  private String createNewSingleNumberReport(ProcessInstanceEngineDto engineDto) {
    String singleReportId = createNewSingleReport();
    ProcessReportDataDto countFlowNodeFrequencyGroupByFlowNode =
      createPiFrequencyCountGroupedByNone(engineDto.getProcessDefinitionKey(), engineDto.getProcessDefinitionVersion());
    SingleProcessReportDefinitionDto definitionDto = new SingleProcessReportDefinitionDto();
    definitionDto.setData(countFlowNodeFrequencyGroupByFlowNode);
    updateReport(singleReportId, definitionDto);
    return singleReportId;
  }

  private String createNewSingleRawReport(ProcessInstanceEngineDto engineDto) {
    String singleReportId = createNewSingleReport();
    ProcessReportDataDto countFlowNodeFrequencyGroupByFlowNode =
      createProcessReportDataViewRawAsTable(engineDto.getProcessDefinitionKey(), engineDto.getProcessDefinitionVersion());
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
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("aProcess" )
      .startEvent(START_EVENT)
      .serviceTask(SERVICE_TASK_ID)
        .camundaExpression("${true}")
      .endEvent(END_EVENT)
      .done();
    return engineRule.deployAndStartProcess(modelInstance);
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

  private CombinedProcessReportResultDto evaluateCombinedReportById(String reportId) {
    return embeddedOptimizeRule
            .getRequestExecutor()
            .buildEvaluateSavedReportRequest(reportId)
      .execute(CombinedProcessReportResultDto.class, 200);
  }

  private CombinedProcessReportResultDto evaluateUnsavedCombined(CombinedReportDataDto reportDataDto) {
    Response response = evaluateUnsavedCombinedReportAndReturnResponse(reportDataDto);

    // then the status code is okay
    assertThat(response.getStatus(), is(200));
    return response.readEntity(CombinedProcessReportResultDto.class);
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
