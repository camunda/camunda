package org.camunda.optimize.service.importing;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.query.ExtendedProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.FilterMapDto;
import org.camunda.optimize.dto.optimize.variable.GetVariablesResponseDto;
import org.camunda.optimize.dto.optimize.query.HeatMapQueryDto;
import org.camunda.optimize.dto.optimize.query.HeatMapResponseDto;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.variable.VariableFilterDto;
import org.camunda.optimize.service.exceptions.OptimizeException;
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

import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.EVENTS;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;

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
    embeddedOptimizeRule.importEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    //then
    allEntriesInElasticsearchHaveAllData(elasticSearchRule.getProcessDefinitionType(), 1L);
  }

  @Test
  public void importProgressReporterStartAndEndImportState() throws OptimizeException {
    // when
    deployAndStartSimpleServiceTask();

    // then
    assertThat(embeddedOptimizeRule.getProgressValue(), is(0));

    // when
    embeddedOptimizeRule.importEngineEntities();

    // then
    assertThat(embeddedOptimizeRule.getProgressValue(), is(100));
  }

  @Test
  public void importProgressReporterIntermediateImportState() throws OptimizeException {
    // given
    deployAndStartSimpleServiceTask();
    embeddedOptimizeRule.importEngineEntities();

    // when
    deployAndStartSimpleServiceTask();

    // then
    assertThat(embeddedOptimizeRule.getProgressValue(), is(50));
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
    embeddedOptimizeRule.importEngineEntities();

    // when
    int importProgress = embeddedOptimizeRule.getProgressValue();

    // then
    assertThat(importProgress, is(100));
  }

  @Test
  public void allProcessDefinitionXmlFieldDataOfImportIsAvailable() throws Exception {
    //given
    deployAndStartSimpleServiceTask();

    //when
    embeddedOptimizeRule.importEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    //then
    allEntriesInElasticsearchHaveAllData(elasticSearchRule.getProcessDefinitionXmlType(), 1L);
  }

  @Test
  public void allEventFieldDataOfImportIsAvailable() throws Exception {
    //given
    deployAndStartSimpleServiceTask();

    //when
    embeddedOptimizeRule.importEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    //then
    allEntriesInElasticsearchHaveAllData(elasticSearchRule.getProcessInstanceType(),1L);
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
    embeddedOptimizeRule.importEngineEntities();
    deployAndStartSimpleServiceTask();
    embeddedOptimizeRule.importEngineEntities();

    // when
    engineRule.finishAllUserTasks();
    embeddedOptimizeRule.importEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // then
    SearchResponse idsResp = getSearchResponseForAllDocumentsOfType(elasticSearchRule.getProcessInstanceType());
    for (SearchHit searchHitFields : idsResp.getHits()) {
      List events = (List) searchHitFields.getSource().get(EVENTS);
      assertThat(events.size(), is(3));
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
    embeddedOptimizeRule.importEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    //then only the start event should be imported as the user task is not finished yet
    SearchResponse idsResp = getSearchResponseForAllDocumentsOfType(elasticSearchRule.getProcessInstanceType());
    assertThat(idsResp.getHits().getTotalHits(), is(1L));
    SearchHit hit = idsResp.getHits().getAt(0);
    List events = (List) hit.getSource().get(EVENTS);
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
    engineRule.deployAndStartProcessWithVariables(processModel, variables);
    embeddedOptimizeRule.importEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    //when
    String token = embeddedOptimizeRule.authenticateAdmin();
    String procDefId = engineRule.getProcessDefinitionId();
    List<GetVariablesResponseDto> variablesResponseDtos = embeddedOptimizeRule.target()
        .path(embeddedOptimizeRule.getProcessDefinitionEndpoint() + "/" + procDefId + "/" + "variables")
        .request()
        .header(HttpHeaders.AUTHORIZATION,"Bearer " + token)
        .get(new GenericType<List<GetVariablesResponseDto>>(){});

    //then
    assertThat(variablesResponseDtos.size(),is(variables.size()));
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
    embeddedOptimizeRule.importEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    //when
     String token = embeddedOptimizeRule.authenticateAdmin();
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
    VariableFilterDto variableFilterDto = new VariableFilterDto();
    variableFilterDto.setName("stringVar");
    variableFilterDto.setType("String");
    variableFilterDto.setOperator("!=");
    variableFilterDto.setValues(Collections.singletonList("aStringValue"));

    FilterMapDto filterMapDto = new FilterMapDto();
    filterMapDto.getVariables().add(variableFilterDto);
    dto.setFilter(filterMapDto);

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
    List<Integer> indexes = embeddedOptimizeRule.getImportIndexes();

    // then
    for (Integer index : indexes) {
      assertThat(index, is(0));
    }
  }

  @Test
  public void latestImportIndexAfterRestartOfOptimize() throws OptimizeException {
    // given
    deployAndStartSimpleServiceTask();
    embeddedOptimizeRule.importEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    embeddedOptimizeRule.stopOptimize();
    embeddedOptimizeRule.startOptimize();
    List<Integer> indexes = embeddedOptimizeRule.getImportIndexes();

    // then
    for (Integer index : indexes) {
      assertThat(index, greaterThan(0));
    }
  }

  @Test
  public void itIsPossibleToResetTheImportIndex() throws OptimizeException {
    // given
    deployAndStartSimpleServiceTask();
    embeddedOptimizeRule.importEngineEntities();
    embeddedOptimizeRule.resetImportStartIndexes();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    embeddedOptimizeRule.stopOptimize();
    embeddedOptimizeRule.startOptimize();
    List<Integer> indexes = embeddedOptimizeRule.getImportIndexes();

    // then
    for (Integer index : indexes) {
      assertThat(index, is(0));
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
    embeddedOptimizeRule.importEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    String token = embeddedOptimizeRule.authenticateAdmin();
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

  private void deployAndStartSimpleServiceTask() {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
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

  private void allEntriesInElasticsearchHaveAllData(String elasticsearchType, long responseCount) {
    SearchResponse idsResp = getSearchResponseForAllDocumentsOfType(elasticsearchType);

    assertThat(idsResp.getHits().getTotalHits(), is(responseCount));
    for (SearchHit searchHit : idsResp.getHits().getHits()) {
      for (Entry searchHitField : searchHit.getSource().entrySet()) {
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

    return elasticSearchRule.getClient().prepareSearch(elasticSearchRule.getOptimizeIndex())
      .setTypes(elasticsearchType)
      .setQuery(qb)
      .setSize(100)
      .get();
  }

}
