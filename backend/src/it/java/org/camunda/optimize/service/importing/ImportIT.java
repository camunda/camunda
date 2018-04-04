package org.camunda.optimize.service.importing;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.result.raw.RawDataReportResultDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableRetrievalDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.rest.optimize.dto.ComplexVariableDto;
import org.camunda.optimize.service.engine.importing.index.handler.DefinitionBasedImportIndexHandler;
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

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.IntStream;

import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.END_DATE;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.EVENTS;
import static org.camunda.optimize.test.util.ReportDataHelper.createReportDataViewRawAsTable;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;


public class ImportIT  {

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  @Test
  public void allProcessDefinitionFieldDataOfImportIsAvailable() {
    //given
    deployAndStartSimpleServiceTask();

    //when
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    //then
    allEntriesInElasticsearchHaveAllData(elasticSearchRule.getProcessDefinitionType());
  }

  @Test
  public void importProgressReporterStartAndEndImportState() {
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
  public void variableImportProgressIsCalculatedCorrectly() {
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
  public void importProgressReporterIntermediateImportState() {
    // given
    deployAndStartSimpleServiceTask();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();

    // when
    deployAndStartSimpleServiceTask();
    embeddedOptimizeRule.resetProcessDefinitionManager();

    // then
    // ( 50 (variable) +
    //   50 (process definitions) +
    //   50 (process definition xmls) +
    //    50 (finished process instances) +
    //    50 (activity instances) ) / 5 = 50
    assertThat(embeddedOptimizeRule.getProgressValue(), is(50L));
  }

  @Test
  public void importProgressReporterConsidersOnlyFinishedHistoricalActivityInstances() {
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
  public void importProgressAfterRestartStaysTheSame() {
    // given
    deployAndStartSimpleServiceTask();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    embeddedOptimizeRule.storeImportIndexesToElasticsearch();
    deployAndStartSimpleServiceTask();
    embeddedOptimizeRule.resetProcessDefinitionManager();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();
    long firstImportProgress = embeddedOptimizeRule.getProgressValue();

    // when
    embeddedOptimizeRule.stopOptimize();
    embeddedOptimizeRule.startOptimize();

    // then
    assertThat(embeddedOptimizeRule.getProgressValue(), is(firstImportProgress));
  }

  @Test
  public void allProcessDefinitionXmlFieldDataOfImportIsAvailable() {
    //given
    deployAndStartSimpleServiceTask();

    //when
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    //then
    allEntriesInElasticsearchHaveAllData(elasticSearchRule.getProcessDefinitionXmlType());
  }

  @Test
  public void allEventFieldDataOfImportIsAvailable() {
    //given
    deployAndStartSimpleServiceTask();

    //when
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    //then
    allEntriesInElasticsearchHaveAllData(elasticSearchRule.getProcessInstanceType());
  }

  @Test
  public void allEventFieldDataOfImportIsAvailableWithAuthentication() {
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
  public void unfinishedActivitiesAreNotSkippedDuringImport() {
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
  public void unfinishedProcessesIndexedAfterFinish() {
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
  public void importOnlyFinishedHistoricActivityInstances() {
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
  public void variableImportWorks() {
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
    List<VariableRetrievalDto> variablesResponseDtos =
      embeddedOptimizeRule.target("variables")
        .queryParam("processDefinitionKey", instanceDto.getProcessDefinitionKey())
        .queryParam("processDefinitionVersion", instanceDto.getProcessDefinitionVersion())
        .request()
        .header(HttpHeaders.AUTHORIZATION,embeddedOptimizeRule.getAuthorizationHeader())
        .get(new GenericType<List<VariableRetrievalDto>>(){});

    //then
    assertThat(variablesResponseDtos.size(),is(variables.size()));
  }

   @Test
  public void variableImportWorksForUnfinishedProcesses() {
    //given
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
          .startEvent()
            .userTask()
          .endEvent()
        .done();

    Map<String, Object> variables = createPrimitiveTypeVariables();
    ProcessInstanceEngineDto instanceDto =
      engineRule.deployAndStartProcessWithVariables(processModel, variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    //when
    List<VariableRetrievalDto> variablesResponseDtos =
      embeddedOptimizeRule.target("variables")
        .queryParam("processDefinitionKey", instanceDto.getProcessDefinitionKey())
        .queryParam("processDefinitionVersion", instanceDto.getProcessDefinitionVersion())
        .request()
        .header(HttpHeaders.AUTHORIZATION,embeddedOptimizeRule.getAuthorizationHeader())
        .get(new GenericType<List<VariableRetrievalDto>>(){});

    //then
    assertThat(variablesResponseDtos.size(),is(variables.size()));
  }

  @Test
  public void variablesWithComplexTypeAreNotImported() {
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
    ProcessInstanceEngineDto instanceDto = deployAndStartSimpleServiceTaskWithVariables(variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<VariableRetrievalDto> variablesResponseDtos =
      embeddedOptimizeRule.target("variables")
        .queryParam("processDefinitionKey", instanceDto.getProcessDefinitionKey())
        .queryParam("processDefinitionVersion", instanceDto.getProcessDefinitionVersion())
        .request()
        .header(HttpHeaders.AUTHORIZATION,embeddedOptimizeRule.getAuthorizationHeader())
        .get(new GenericType<List<VariableRetrievalDto>>(){});

    //then
    assertThat(variablesResponseDtos.size(),is(0));
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
  public void indexIsIncrementedEvenAfterReset() {
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
  public void importProgressIfUnfinishedProcessInstancesGetFinished() {
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
  public void latestImportIndexAfterRestartOfOptimize() {
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
  public void indexAfterRestartOfOptimizeHasCorrectProcessDefinitionsToImport() {
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
  public void afterRestartOfOptimizeAlsoNewDataIsImported() {
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
  public void itIsPossibleToResetTheImportIndex() {
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
  public void importProgressContinuesAfterRestartOnceNewDataAppears() {
    // given
    ProcessInstanceEngineDto process1 = deployAndStartSimpleServiceTask();
    ProcessInstanceEngineDto process2 = deployAndStartSimpleServiceTask();

    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    ReportDataDto reportData =
      createReportDataViewRawAsTable(process1.getProcessDefinitionKey(), process1.getProcessDefinitionVersion());
    RawDataReportResultDto result = evaluateReport(reportData);

    assertThat(result.getResult().size(), is(1));

    reportData =
      createReportDataViewRawAsTable(process2.getProcessDefinitionKey(), process2.getProcessDefinitionVersion());
    result = evaluateReport(reportData);

    assertThat(result.getResult().size(), is(1));
    assertThat(embeddedOptimizeRule.getProgressValue(), is(100L));

    //when
    ProcessInstanceEngineDto process3 = engineRule.startProcessInstance(process2.getDefinitionId());
    assertThat(process2.getId(), is(not(process3.getId())));
    assertThat(process2.getDefinitionId(), is(process3.getDefinitionId()));

    embeddedOptimizeRule.restartImportCycle();
    embeddedOptimizeRule.importWithoutReset();

    reportData = createReportDataViewRawAsTable(process2.getProcessDefinitionKey(), process2.getProcessDefinitionVersion());
    result = evaluateReport(reportData);

    // then
    assertThat(result.getResult().size(), is(2));
    assertThat(embeddedOptimizeRule.getProgressValue(), is(100L));
  }

  @Test
  public void importProgressContinuesAfterResetOnceNewDataAppears() {
    // given
    ProcessInstanceEngineDto process1 = deployAndStartSimpleServiceTask();
    ProcessInstanceEngineDto process2 = deployAndStartSimpleServiceTask();

    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    ReportDataDto reportData =
      createReportDataViewRawAsTable(process1.getProcessDefinitionKey(), process1.getProcessDefinitionVersion());
    RawDataReportResultDto result = evaluateReport(reportData);

    assertThat(result.getResult().size(), is(1));

    reportData =
      createReportDataViewRawAsTable(process2.getProcessDefinitionKey(), process2.getProcessDefinitionVersion());
    result = evaluateReport(reportData);

    assertThat(result.getResult().size(), is(1));
    assertThat(embeddedOptimizeRule.getProgressValue(), is(100L));

    //when
    ProcessInstanceEngineDto process3 = engineRule.startProcessInstance(process2.getDefinitionId());
    assertThat(process2.getId(), is(not(process3.getId())));
    assertThat(process2.getDefinitionId(), is(process3.getDefinitionId()));

    //once new round starts reset will happen instead of restart
    embeddedOptimizeRule.resetImportStartIndexes();
    embeddedOptimizeRule.importWithoutReset();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    reportData =
      createReportDataViewRawAsTable(process2.getProcessDefinitionKey(), process2.getProcessDefinitionVersion());
    result = evaluateReport(reportData);

    // then
    assertThat(result.getResult().size(), is(2));
    assertThat(embeddedOptimizeRule.getProgressValue(), is(100L));

  }

  @Test
  public void restartDoesNotAffectProgress() throws Exception {
    deployAndStartSimpleServiceTask();
    deployAndStartSimpleServiceTask();
    engineRule.waitForAllProcessesToFinish();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();

    elasticSearchRule.refreshOptimizeIndexInElasticsearch();
    assertThat(embeddedOptimizeRule.getProgressValue(), is(100L));

    embeddedOptimizeRule.restartImportCycle();
    
    assertThat(embeddedOptimizeRule.getProgressValue(), is(100L));
  }

  private RawDataReportResultDto evaluateReport(ReportDataDto reportData) {
    Response response = evaluateReportAndReturnResponse(reportData);
    assertThat(response.getStatus(), is(200));

    return response.readEntity(RawDataReportResultDto.class);
  }

  private Response evaluateReportAndReturnResponse(ReportDataDto reportData) {
    return embeddedOptimizeRule.target("report/evaluate")
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
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
