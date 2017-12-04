package org.camunda.optimize.service.importing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.rest.engine.dto.DeploymentDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.EVENTS;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isIn;
import static org.junit.Assert.fail;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/it/it-applicationContext.xml"})
public class ProcessDefinitionBaseImportIT {

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  private ConfigurationService configurationService;

  @Before
  public void setup() throws IOException {
    configurationService = embeddedOptimizeRule.getConfigurationService();
  }

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  @Test
  public void importProgressReporterStartAndEndImportState() throws IOException, OptimizeException {
    createAndSetProcessDefinition(createSimpleServiceTaskProcess());

    // then
    assertThat(embeddedOptimizeRule.getProgressValue(), is(0L));

    // when
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();

    // then
    assertThat(embeddedOptimizeRule.getProgressValue(), is(100L));
  }

  @Test
  public void importProgressTakesOnlyProcessDefinitionToImportIntoAccount() throws OptimizeException, IOException {
    // given
    deployAndStartSimpleServiceTask();
    createAndSetProcessDefinition(createSimpleServiceTaskProcess());

    // when
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();

    // then
    assertThat(embeddedOptimizeRule.getProgressValue(), is(100L));
  }

  @Test
  public void importOnlyDataToGivenProcessDefinitionId() throws IOException, OptimizeException {
    // given
    deployAndStartSimpleServiceTask();
    String processDefinitionId = createAndSetProcessDefinition(createSimpleServiceTaskProcess());

    // when
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();

    // then
    allEntriesInElasticsearchHaveAllData(configurationService.getProcessInstanceType(), processDefinitionId);
    allEntriesInElasticsearchHaveAllData(configurationService.getProcessDefinitionXmlType(), processDefinitionId);
    allEntriesInElasticsearchHaveAllData(configurationService.getProcessDefinitionType(), processDefinitionId);
  }

  @Test
  public void latestImportIndexAfterRestartOfOptimize() throws OptimizeException, IOException {
    // given
    createAndSetProcessDefinition(createSimpleServiceTaskProcess());
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
  public void itIsPossibleToResetTheImportIndex() throws OptimizeException, IOException {
    // given
    createAndSetProcessDefinition(createSimpleServiceTaskProcess());
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
  public void unfinishedActivitiesAreNotSkippedDuringImport() throws OptimizeException, IOException {
    // given
    createAndSetProcessDefinition(createSimpleUserTaskProcess());
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();

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
  public void importOnlyFinishedHistoricActivityInstances() throws Exception {
    //given
    createAndSetProcessDefinition(createSimpleUserTaskProcess());

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
  public void testDataForLatestProcessVersionImportedFirst() throws Exception {
    //given
    BpmnModelInstance simpleUserTaskProcess = createSimpleUserTaskProcess();
    engineRule.deployAndStartProcess(simpleUserTaskProcess);
    ProcessInstanceEngineDto latestProcessInstance = engineRule.deployAndStartProcess(simpleUserTaskProcess);
    String latestPd = latestProcessInstance.getDefinitionId();
    embeddedOptimizeRule.updateImportIndex();

    //when
    embeddedOptimizeRule.scheduleImport();

    //then
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    SearchResponse idsResp = getSearchResponseForAllDocumentsOfType(elasticSearchRule.getProcessInstanceType());
    for (SearchHit searchHitFields : idsResp.getHits()) {
      assertThat(searchHitFields.getSourceAsMap().get("processDefinitionId"), is(latestPd));
    }

  }

  @Test
  public void testDataForLatestProcessVersionsImportedFirst() throws Exception {
    //given
    int oldPageSize = configurationService.getEngineImportProcessInstanceMaxPageSize();
    configurationService.setEngineImportProcessInstanceMaxPageSize(1);
    ArrayList<String> ids = new ArrayList<>();

    BpmnModelInstance simpleServiceTaskProcess = createSimpleServiceTaskProcess();
    engineRule.deployAndStartProcess(simpleServiceTaskProcess);

    ProcessInstanceEngineDto latestProcessInstance = engineRule.deployAndStartProcess(simpleServiceTaskProcess);
    String latestPd = latestProcessInstance.getDefinitionId();
    ids.add(latestPd);

    simpleServiceTaskProcess = createSimpleServiceTaskProcess();
    latestProcessInstance = engineRule.deployAndStartProcess(simpleServiceTaskProcess);
    latestPd = latestProcessInstance.getDefinitionId();
    ids.add(latestPd);

    embeddedOptimizeRule.updateImportIndex();

    //when
    embeddedOptimizeRule.scheduleImport();

    //then
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    SearchResponse idsResp = getSearchResponseForAllDocumentsOfType(elasticSearchRule.getProcessInstanceType());
    for (SearchHit searchHitFields : idsResp.getHits()) {
      assertThat(idsResp.getHits().totalHits, is((long) ids.size()));
      assertThat((String) searchHitFields.getSourceAsMap().get("processDefinitionId"), isIn(ids));
    }
    configurationService.setEngineImportProcessInstanceMaxPageSize(oldPageSize);
  }

  private void allEntriesInElasticsearchHaveAllData(String elasticsearchType, String expectedProcessDefinitionId) throws IOException {
    SearchResponse idsResp = getSearchResponseForAllDocumentsOfType(elasticsearchType);

    for (SearchHit searchHit : idsResp.getHits().getHits()) {
      ObjectNode node = new ObjectMapper().readValue(searchHit.getSourceAsString(), ObjectNode.class);
      String storedProcessDefinitionId = "";
      if (node.has("processDefinitionId")) {
        storedProcessDefinitionId = node.get("processDefinitionId").asText();
      } else if (node.has("id")) {
        storedProcessDefinitionId = node.get("id").asText();
      } else {
        fail("Search hit should contain a process definition id!");
      }
      assertThat(storedProcessDefinitionId, is(expectedProcessDefinitionId));
    }
  }

  private SearchResponse getSearchResponseForAllDocumentsOfType(String elasticsearchType) {
    QueryBuilder qb = matchAllQuery();

    return elasticSearchRule.getClient()
      .prepareSearch(elasticSearchRule.getOptimizeIndex(elasticsearchType))
      .setTypes(elasticsearchType)
      .setQuery(qb)
      .setSize(100)
      .setFetchSource(true)
      .get();
  }

  private String createAndSetProcessDefinition(BpmnModelInstance modelInstance) throws IOException {
    String processDefinitionId = createAndStartProcessDefinition(modelInstance);
    List<String> processDefinitionIdsToImport = new ArrayList<>();
    processDefinitionIdsToImport.add(processDefinitionId);
    configurationService.setProcessDefinitionIdsToImport(processDefinitionIdsToImport);
    embeddedOptimizeRule.reloadConfiguration();
    return processDefinitionId;
  }

  private String createAndAddProcessDefinitionToImportList(BpmnModelInstance modelInstance) throws IOException {
    String processDefinitionId = createAndStartProcessDefinition(modelInstance);
    addProcessDefinitionIdToImportList(processDefinitionId);
    embeddedOptimizeRule.resetImportStartIndexes();
    return processDefinitionId;
  }

  private String createAndStartProcessDefinition(BpmnModelInstance modelInstance) throws IOException {
    CloseableHttpClient client = HttpClientBuilder.create().build();
    DeploymentDto deploymentDto = engineRule.deployProcess(modelInstance, client);
    List<ProcessDefinitionEngineDto> list = engineRule.getAllProcessDefinitions(deploymentDto, client);
    assertThat(list.size(), is(1));
    String processDefinitionId = list.get(0).getId();
    engineRule.startProcessInstance(processDefinitionId, client);
    client.close();
    return processDefinitionId;
  }

  private BpmnModelInstance createSimpleServiceTaskProcess() {
    return Bpmn.createExecutableProcess("ASimpleServiceTaskProcess" + System.currentTimeMillis())
      .startEvent()
      .serviceTask()
        .camundaExpression("${true}")
      .endEvent()
      .done();
  }

  private BpmnModelInstance createSimpleUserTaskProcess() {
    return Bpmn.createExecutableProcess("ASimpleUserTaskProcess" + System.currentTimeMillis())
        .startEvent()
          .userTask()
        .endEvent()
      .done();
  }

  private void addProcessDefinitionIdToImportList(String processDefinitionId) {
    List<String> procDefsToImport = configurationService.getProcessDefinitionIdsToImport();
    procDefsToImport.add(processDefinitionId);
    embeddedOptimizeRule.reloadConfiguration();
  }

  private void deployAndStartSimpleServiceTask() {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess" + System.currentTimeMillis())
      .name("aProcessName")
        .startEvent()
        .serviceTask()
          .camundaExpression("${true}")
        .endEvent()
      .done();
    Map<String, Object> variables = new HashMap<>();
    variables.put("aVariable", "aStringVariables");
    engineRule.deployAndStartProcessWithVariables(processModel, variables);
  }

  @Test
  public void importProgressReporterIntermediateImportState() throws OptimizeException, IOException {
    // given
    embeddedOptimizeRule.resetImportStartIndexes();
    createAndSetProcessDefinition(createSimpleServiceTaskProcess());
    createAndAddProcessDefinitionToImportList(createSimpleServiceTaskProcess());

    // when
    embeddedOptimizeRule.scheduleImport();

    // then
    assertThat(embeddedOptimizeRule.getProgressValue(), is(50L));
  }


}
