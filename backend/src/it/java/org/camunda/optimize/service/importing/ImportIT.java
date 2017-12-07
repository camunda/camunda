package org.camunda.optimize.service.importing;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.ExtendedProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.HeatMapQueryDto;
import org.camunda.optimize.dto.optimize.query.HeatMapResponseDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.ViewDto;
import org.camunda.optimize.dto.optimize.query.report.filter.VariableFilterDto;
import org.camunda.optimize.dto.optimize.query.report.filter.data.VariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.result.raw.RawDataReportResultDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableRetrievalDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.rest.optimize.dto.ComplexVariableDto;
import org.camunda.optimize.service.engine.importing.index.handler.DefinitionBasedImportIndexHandler;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.util.configuration.EngineConfiguration;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.IntStream;

import static org.camunda.optimize.service.es.filter.FilterOperatorConstants.NOT_IN;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_RAW_DATA_OPERATION;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.END_DATE;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.EVENTS;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;


@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/it/it-applicationContext.xml"})
public class ImportIT  {
  private static final String SUB_PROCESS_ID = "testProcess";
  private static final String CALL_ACTIVITY = "callActivity";
  private static final String TEST_MI_PROCESS = "testMIProcess";

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  @Test
  public void allProcessDefinitionFieldDataOfImportIsAvailable() throws Exception {
    //given
    deployAndStartSimpleServiceTask();

    //when
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    //then
    allEntriesInElasticsearchHaveAllData(elasticSearchRule.getProcessDefinitionType());
  }

  @Test
  public void importProgressReporterStartAndEndImportState() throws OptimizeException {
    // when
    deployAndStartSimpleServiceTask();
    embeddedOptimizeRule.resetImportStartIndexes();

    // then
    assertThat(embeddedOptimizeRule.getProgressValue(), is(0L));

    // when
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();

    // then
    assertThat(embeddedOptimizeRule.getProgressValue(), is(100L));
  }

  @Test
  public void variableImportProgressIsCalculatedCorrectly() throws Exception {
    // given
    Map<String, Object> variables = new HashMap<>();
    IntStream.range(0, 15).forEach(i -> variables.put("var"+i, i));
    deployAndStartSimpleServiceTaskWithVariables(variables);

    // when
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // then
    assertThat(embeddedOptimizeRule.getProgressValue(), is(100L));
  }

  @Test
  public void importProgressReporterIntermediateImportState() throws OptimizeException {
    // given
    deployAndStartSimpleServiceTask();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();

    // when
    deployAndStartSimpleServiceTask();

    // then
    // ( 100 (variable) +
    //   50 (process definitions) +
    //   50 (process definition xmls) +
    //    50 (finished process instances) +
    //    50 (activity instances) ) / 5 = 60
    assertThat(embeddedOptimizeRule.getProgressValue(), is(60L));
  }

  @Test
  public void importProgressReporterConsidersOnlyFinishedHistoricalActivityInstances() throws OptimizeException {
    // given
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
        .startEvent()
        .userTask()
        .endEvent()
      .done();
    engineRule.deployAndStartProcess(processModel);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();

    // when
    long importProgress = embeddedOptimizeRule.getProgressValue();

    // then
    assertThat(importProgress, is(100L));
  }

  @Test
  public void importProgressAfterRestartStaysTheSame() throws Exception {
    // given
    deployAndStartSimpleServiceTask();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    embeddedOptimizeRule.storeImportIndexesToElasticsearch();
    deployAndStartSimpleServiceTask();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();
    long firstImportProgress = embeddedOptimizeRule.getProgressValue();

    // when
    embeddedOptimizeRule.stopOptimize();
    embeddedOptimizeRule.startOptimize();

    // then
    assertThat(embeddedOptimizeRule.getProgressValue(), is(firstImportProgress));
  }

  @Test
  public void allProcessDefinitionXmlFieldDataOfImportIsAvailable() throws Exception {
    //given
    deployAndStartSimpleServiceTask();

    //when
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    //then
    allEntriesInElasticsearchHaveAllData(elasticSearchRule.getProcessDefinitionXmlType());
  }

  @Test
  public void allEventFieldDataOfImportIsAvailable() throws Exception {
    //given
    deployAndStartSimpleServiceTask();

    //when
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    //then
    allEntriesInElasticsearchHaveAllData(elasticSearchRule.getProcessInstanceType());
  }

  @Test
  public void allEventFieldDataOfImportIsAvailableWithAuthentication() throws Exception {
    //given
    EngineConfiguration engineConfiguration = embeddedOptimizeRule
      .getConfigurationService().getConfiguredEngines().get("1");
    engineConfiguration.getAuthentication().setEnabled(true);
    engineConfiguration.getAuthentication().setPassword("demo");
    engineConfiguration.getAuthentication().setUser("demo");
    engineConfiguration.setRest("http://localhost:48080/engine-rest-secure");
    engineRule.addUser("demo", "demo");
    embeddedOptimizeRule.reloadConfiguration();
    deployAndStartSimpleServiceTask();

    //when
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    //then
    allEntriesInElasticsearchHaveAllData(elasticSearchRule.getProcessInstanceType());

    engineConfiguration.getAuthentication().setEnabled(false);
    engineConfiguration.setRest("http://localhost:48080/engine-rest");
  }

  @Test
  public void unfinishedActivitiesAreNotSkippedDuringImport() throws OptimizeException {
    // given
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
        .startEvent()
        .userTask()
        .endEvent()
        .done();
    engineRule.deployAndStartProcess(processModel);
    deployAndStartSimpleServiceTask();

    // when
    engineRule.finishAllUserTasks();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // then
    SearchResponse idsResp = getSearchResponseForAllDocumentsOfType(elasticSearchRule.getProcessInstanceType());
    for (SearchHit searchHitFields : idsResp.getHits()) {
      List events = (List) searchHitFields.getSourceAsMap().get(EVENTS);
      assertThat(events.size(), is(3));
    }
  }

  @Test
  public void unfinishedProcessesIndexedAfterFinish() throws OptimizeException {
    // given
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
        .startEvent()
        .userTask()
        .endEvent()
        .done();
    engineRule.deployAndStartProcess(processModel);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();

    //then
    SearchResponse idsResp = getSearchResponseForAllDocumentsOfType(elasticSearchRule.getProcessInstanceType());
    for (SearchHit searchHitFields : idsResp.getHits()) {
      List events = (List) searchHitFields.getSourceAsMap().get(EVENTS);
      assertThat(events.size(), is(1));
      Object date = searchHitFields.getSourceAsMap().get(END_DATE);
      assertThat(date, is(nullValue()));
    }

    // when
    engineRule.finishAllUserTasks();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // then
    idsResp = getSearchResponseForAllDocumentsOfType(elasticSearchRule.getProcessInstanceType());
    for (SearchHit searchHitFields : idsResp.getHits()) {
      Object date = searchHitFields.getSourceAsMap().get(END_DATE);
      assertThat(date, is(notNullValue()));
    }
  }

  @Test
  public void importOnlyFinishedHistoricActivityInstances() throws Exception {
    //given
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
        .startEvent()
        .userTask()
        .endEvent()
      .done();
    engineRule.deployAndStartProcess(processModel);

    //when
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    //then only the start event should be imported as the user task is not finished yet
    SearchResponse idsResp = getSearchResponseForAllDocumentsOfType(elasticSearchRule.getProcessInstanceType());
    assertThat(idsResp.getHits().getTotalHits(), is(1L));
    SearchHit hit = idsResp.getHits().getAt(0);
    List events = (List) hit.getSourceAsMap().get(EVENTS);
    assertThat(events.size(), is(1));
  }

  @Test
  public void variableImportWorks() throws Exception {
    //given
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
        .startEvent()
        .serviceTask()
          .camundaExpression("${true}")
        .endEvent()
      .done();

    Map<String, Object> variables = createPrimitiveTypeVariables();
    ProcessInstanceEngineDto instanceDto =
      engineRule.deployAndStartProcessWithVariables(processModel, variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    //when
    String token = embeddedOptimizeRule.getAuthenticationToken();
    String procDefId = instanceDto.getDefinitionId();
    List<VariableRetrievalDto> variablesResponseDtos =
      embeddedOptimizeRule.target("variables/" + procDefId)
        .request()
        .header(HttpHeaders.AUTHORIZATION,"Bearer " + token)
        .get(new GenericType<List<VariableRetrievalDto>>(){});

    //then
    assertThat(variablesResponseDtos.size(),is(variables.size()));
  }

  @Test
  public void variablesWithComplexTypeAreNotImported() throws OptimizeException {
    // given
    ComplexVariableDto complexVariableDto = new ComplexVariableDto();
    complexVariableDto.setType("Object");
    complexVariableDto.setValue(null);
    ComplexVariableDto.ValueInfo info = new ComplexVariableDto.ValueInfo();
    info.setObjectTypeName("java.util.ArrayList");
    info.setSerializationDataFormat("application/x-java-serialized-object");
    complexVariableDto.setValueInfo(info);
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", complexVariableDto);
    deployAndStartSimpleServiceTaskWithVariables(variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    String token = embeddedOptimizeRule.getAuthenticationToken();
    String procDefId = engineRule.getProcessDefinitionId();
    List<VariableRetrievalDto> variablesResponseDtos =
      embeddedOptimizeRule.target("variables/" + procDefId)
        .request()
        .header(HttpHeaders.AUTHORIZATION,"Bearer " + token)
        .get(new GenericType<List<VariableRetrievalDto>>(){});

    //then
    assertThat(variablesResponseDtos.size(),is(0));
  }

  @Test
  public void variableFilterWorks() throws Exception {
    //given
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
        .startEvent()
        .serviceTask()
          .camundaExpression("${true}")
        .endEvent()
      .done();

    Map<String, Object> variables = createPrimitiveTypeVariables();
    engineRule.deployAndStartProcessWithVariables(processModel, variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    //when
    String token = embeddedOptimizeRule.getAuthenticationToken();
    List<ExtendedProcessDefinitionOptimizeDto> definitions = embeddedOptimizeRule.target()
        .path(embeddedOptimizeRule.getProcessDefinitionEndpoint())
        .request()
        .header(HttpHeaders.AUTHORIZATION,"Bearer " + token)
        .get(new GenericType<List<ExtendedProcessDefinitionOptimizeDto>>(){});
    assertThat(definitions.size(),is(1));

    String id = definitions.get(0).getId();
    assertThat(id, is(notNullValue()));

    //when
    HeatMapQueryDto dto = new HeatMapQueryDto();
    dto.setProcessDefinitionId(id);

    VariableFilterDataDto data = new VariableFilterDataDto();
    data.setName("stringVar");
    data.setType("String");
    data.setOperator(NOT_IN);
    data.setValues(Collections.singletonList("aStringValue"));
    VariableFilterDto variableFilterDto = new VariableFilterDto();
    variableFilterDto.setData(data);
    dto.setFilter(Collections.singletonList(variableFilterDto));

    HeatMapResponseDto heatMap = getHeatMapResponseDto(token, dto);
    //then
    assertThat(heatMap.getPiCount(), is(0L));
  }

  private HeatMapResponseDto getHeatMapResponseDto(String token, HeatMapQueryDto dto) {
    Response response = getResponse(token, dto);

    // then the status code is okay
    return response.readEntity(HeatMapResponseDto.class);
  }

  private Response getResponse(String token, HeatMapQueryDto dto) {
    Entity<HeatMapQueryDto> entity = Entity.entity(dto, MediaType.APPLICATION_JSON);
    return embeddedOptimizeRule.target("process-definition/heatmap/frequency")
        .request()
        .header(HttpHeaders.AUTHORIZATION,"Bearer " + token)
        .post(entity);
  }

  private Map<String, Object> createPrimitiveTypeVariables() {
    Map<String, Object> variables = new HashMap<>();
    Integer integer = 1;
    variables.put("stringVar", "aStringValue");
    variables.put("boolVar", true);
    variables.put("integerVar", integer);
    variables.put("shortVar", integer.shortValue());
    variables.put("longVar", 1L);
    variables.put("doubleVar", 1.1);
    variables.put("dateVar", new Date());
    return variables;
  }

  @Test
  public void importIndexIsZeroIfNothingIsImportedYet() {
    // when
    List<Long> indexes = embeddedOptimizeRule.getImportIndexes();

    // then
    for (Long index : indexes) {
      assertThat(index, is(0L));
    }
  }

  @Test
  public void indexIsIncrementedEvenAfterReset() throws Exception {
    // given
    deployAndStartSimpleServiceTask();
    deployAndStartSimpleServiceTask();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();
    List<Long> firstRoundIndexes =  embeddedOptimizeRule.getImportIndexes();

    // then
    embeddedOptimizeRule.resetImportStartIndexes();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    List<Long> secondsRoundIndexes = embeddedOptimizeRule.getImportIndexes();

    // then
    for (int i = 0; i < firstRoundIndexes.size(); i++) {
      assertThat(firstRoundIndexes.get(i), is(secondsRoundIndexes.get(i)));
    }
  }

  @Test
  public void importProgressIfUnfinishedProcessInstancesGetFinished() throws Exception {
    // given
    deployAndStartSimpleUserTask();
    deployAndStartSimpleUserTask();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    deployAndStartSimpleUserTask();
    engineRule.finishAllUserTasks();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // then
    assertThat(embeddedOptimizeRule.getProgressValue(), is(100L));
  }

  @Test
  public void latestImportIndexAfterRestartOfOptimize() throws OptimizeException {
    // given
    deployAndStartSimpleServiceTask();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    embeddedOptimizeRule.storeImportIndexesToElasticsearch();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    embeddedOptimizeRule.stopOptimize();
    embeddedOptimizeRule.startOptimize();
    List<Long> indexes = embeddedOptimizeRule.getImportIndexes();

    // then
    for (Long index : indexes) {
      assertThat(index, greaterThan(0L));
    }
  }

  @Test
  public void indexAfterRestartOfOptimizeHasCorrectProcessDefinitionsToImport() throws OptimizeException {
    // given
    deployAndStartSimpleServiceTask();
    deployAndStartSimpleServiceTask();
    deployAndStartSimpleServiceTask();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    embeddedOptimizeRule.storeImportIndexesToElasticsearch();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    embeddedOptimizeRule.stopOptimize();
    embeddedOptimizeRule.startOptimize();
    List<DefinitionBasedImportIndexHandler> handler = embeddedOptimizeRule.getDefinitionBasedImportIndexHandler();

    // then
    for (DefinitionBasedImportIndexHandler definitionBasedImportIndexHandler : handler) {
      assertThat(definitionBasedImportIndexHandler.hasStillNewDefinitionsToImport(), is(false));
    }
  }

  @Test
  public void afterRestartOfOptimizeAlsoNewDataIsImported() throws OptimizeException {
    // given
    deployAndStartSimpleServiceTask();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();
    List<Long> firstRoundIndexes = embeddedOptimizeRule.getImportIndexes();

    // and
    deployAndStartSimpleServiceTask();

    // when
    embeddedOptimizeRule.stopOptimize();
    embeddedOptimizeRule.startOptimize();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();
    List<Long> secondsRoundIndexes = embeddedOptimizeRule.getImportIndexes();

    // then
    for (int i = 0; i < firstRoundIndexes.size(); i++) {
      assertThat(firstRoundIndexes.get(i), lessThanOrEqualTo(secondsRoundIndexes.get(i)));
    }
  }

  @Test
  public void itIsPossibleToResetTheImportIndex() throws OptimizeException {
    // given
    deployAndStartSimpleServiceTask();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    embeddedOptimizeRule.resetImportStartIndexes();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    embeddedOptimizeRule.stopOptimize();
    embeddedOptimizeRule.startOptimize();
    List<Long> indexes = embeddedOptimizeRule.getImportIndexes();

    // then
    for (Long index : indexes) {
      assertThat(index, is(0L));
    }
  }

  @Test
  public void importWithMi() throws Exception {
    //given
    BpmnModelInstance subProcess = Bpmn.createExecutableProcess(SUB_PROCESS_ID)
        .startEvent()
          .serviceTask("MI-Body-Task")
            .camundaExpression("${true}")
        .endEvent()
        .done();
    CloseableHttpClient client = HttpClientBuilder.create().build();
    engineRule.deployProcess(subProcess, client);
    client.close();

    BpmnModelInstance model = Bpmn.createExecutableProcess(TEST_MI_PROCESS)
        .name("MultiInstance")
          .startEvent("miStart")
          .parallelGateway()
            .endEvent("end1")
          .moveToLastGateway()
            .callActivity(CALL_ACTIVITY)
            .calledElement(SUB_PROCESS_ID)
            .multiInstance()
              .cardinality("2")
            .multiInstanceDone()
          .endEvent("miEnd")
        .done();
    engineRule.deployAndStartProcess(model);

    engineRule.waitForAllProcessesToFinish();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    String token = embeddedOptimizeRule.getAuthenticationToken();
    List<ExtendedProcessDefinitionOptimizeDto> definitions = embeddedOptimizeRule.target()
        .path(embeddedOptimizeRule.getProcessDefinitionEndpoint())
        .request()
        .header(HttpHeaders.AUTHORIZATION,"Bearer " + token)
        .get(new GenericType<List<ExtendedProcessDefinitionOptimizeDto>>(){});
    assertThat(definitions.size(),is(2));

    String id = null;
    for (ProcessDefinitionOptimizeDto dto : definitions) {
      if (TEST_MI_PROCESS.equals(dto.getKey())) {
        id = dto.getId();
      }
    }
    assertThat(id, is(notNullValue()));


    //when
    HeatMapResponseDto heatMap = embeddedOptimizeRule.target()
        .path(embeddedOptimizeRule.getProcessDefinitionEndpoint() + "/" + id + "/" + "heatmap/frequency")
        .request()
        .header(HttpHeaders.AUTHORIZATION,"Bearer " + token)
        .get(HeatMapResponseDto.class);

    //then
    assertThat(heatMap.getPiCount(), is(1L));
    assertThat(heatMap.getFlowNodes().size(), is(5));
  }

  @Test
  public void importProgressContinuesAfterResetOnceNewDataAppears() throws Exception {
    ProcessInstanceEngineDto process1 = deployAndStartSimpleServiceTask();
    ProcessInstanceEngineDto process2 = deployAndStartSimpleServiceTask();
    engineRule.waitForAllProcessesToFinish();
    long initialBackoff = embeddedOptimizeRule.getConfigurationService().getMaximumBackoff();
    embeddedOptimizeRule.getConfigurationService().setMaximumBackoff(2l);
    embeddedOptimizeRule.getConfigurationService().setBackoffEnabled(true);
    embeddedOptimizeRule.reloadConfiguration();

    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntitiesWithoutReset();

    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    ReportDataDto reportData = createDefaultReportData(process1.getDefinitionId());
    RawDataReportResultDto result = evaluateReport(reportData);

    assertThat(result.getResult().size(), is(1));

    reportData = createDefaultReportData(process2.getDefinitionId());
    result = evaluateReport(reportData);

    assertThat(result.getResult().size(), is(1));

    //when
    ProcessInstanceEngineDto targetProcess = process2;
    ProcessInstanceEngineDto process3 = engineRule.startProcessInstance(targetProcess.getDefinitionId());
    assertThat(targetProcess.getId(), is(not(process3.getId())));
    assertThat(targetProcess.getDefinitionId(), is(process3.getDefinitionId()));

    engineRule.waitForAllProcessesToFinish();

    embeddedOptimizeRule.waitForBackoff();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntitiesWithoutReset();

    reportData = createDefaultReportData(targetProcess.getDefinitionId());
    result = evaluateReport(reportData);

    assertThat(result.getResult().size(), is(2));

    //after
    embeddedOptimizeRule.getConfigurationService().setMaximumBackoff(initialBackoff);
    embeddedOptimizeRule.getConfigurationService().setBackoffEnabled(false);
    embeddedOptimizeRule.reloadConfiguration();
  }

  @Test
  public void restartDoesNotAffectProgress() throws Exception {
    deployAndStartSimpleServiceTask();
    deployAndStartSimpleServiceTask();
    engineRule.waitForAllProcessesToFinish();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntitiesWithoutReset();

    elasticSearchRule.refreshOptimizeIndexInElasticsearch();
    assertThat(embeddedOptimizeRule.getProgressValue(), is(100L));

    embeddedOptimizeRule.restartImportCycle();
    
    assertThat(embeddedOptimizeRule.getProgressValue(), is(100L));
  }

  private ReportDataDto createDefaultReportData(String processDefinitionId) {
    ReportDataDto reportData = new ReportDataDto();
    reportData.setProcessDefinitionId(processDefinitionId);
    reportData.setVisualization("table");
    reportData.setView(new ViewDto(VIEW_RAW_DATA_OPERATION));
    return reportData;
  }

  private RawDataReportResultDto evaluateReport(ReportDataDto reportData) {
    Response response = evaluateReportAndReturnResponse(reportData);
    assertThat(response.getStatus(), is(200));

    return response.readEntity(RawDataReportResultDto.class);
  }

  private Response evaluateReportAndReturnResponse(ReportDataDto reportData) {
    String token = embeddedOptimizeRule.getAuthenticationToken();
    return embeddedOptimizeRule.target("report/evaluate")
        .request()
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .post(Entity.json(reportData));
  }

  private ProcessInstanceEngineDto deployAndStartSimpleServiceTask() {
    Map<String, Object> variables = new HashMap<>();
    variables.put("aVariable", "aStringVariables");
    return deployAndStartSimpleServiceTaskWithVariables(variables);
  }

  private ProcessInstanceEngineDto deployAndStartSimpleServiceTaskWithVariables(Map<String, Object> variables) {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
        .startEvent()
        .serviceTask()
          .camundaExpression("${true}")
        .endEvent()
      .done();
    return engineRule.deployAndStartProcessWithVariables(processModel, variables);
  }

  private void deployAndStartSimpleUserTask() {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("ASimpleUserTaskProcess" + System.currentTimeMillis())
        .startEvent()
          .userTask()
        .endEvent()
      .done();
    engineRule.deployAndStartProcess(processModel);
  }

  private void allEntriesInElasticsearchHaveAllData(String elasticsearchType) {
    SearchResponse idsResp = getSearchResponseForAllDocumentsOfType(elasticsearchType);

    assertThat(idsResp.getHits().getTotalHits(), is(1L));
    for (SearchHit searchHit : idsResp.getHits().getHits()) {
      for (Entry searchHitField : searchHit.getSourceAsMap().entrySet()) {
        String errorMessage = "Something went wrong during fetching of field: " + searchHitField.getKey() +
          ". Should actually have a value!";
        assertThat(errorMessage, searchHitField.getValue(), is(notNullValue()));
        if (searchHitField.getValue() instanceof String) {
          String value = (String) searchHitField.getValue();
          assertThat(errorMessage, value.isEmpty(), is(false));
        }
      }
    }
  }

  private SearchResponse getSearchResponseForAllDocumentsOfType(String elasticsearchType) {
    QueryBuilder qb = matchAllQuery();

    return elasticSearchRule.getClient().prepareSearch(elasticSearchRule.getOptimizeIndex(elasticsearchType))
      .setTypes(elasticsearchType)
      .setQuery(qb)
      .setSize(100)
      .get();
  }

}
