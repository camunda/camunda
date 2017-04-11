package org.camunda.optimize.service.importing;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.ExtendedProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.HeatMapResponseDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.test.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.rule.EngineIntegrationRule;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.HttpHeaders;
import java.util.List;
import java.util.Map.Entry;

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
  public void importProgressReporterStartAndEndImportState() {
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
  public void importProgressReporterItermediateImportState() {
    // given
    deployAndStartSimpleServiceTask();
    embeddedOptimizeRule.importEngineEntities();

    // when
    deployAndStartSimpleServiceTask();

    // then
    assertThat(embeddedOptimizeRule.getProgressValue(), is(50));
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
    allEntriesInElasticsearchHaveAllData(elasticSearchRule.getEventType(), 3L);
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
    embeddedOptimizeRule.importEngineEntities();
    deployAndStartSimpleServiceTask();
    embeddedOptimizeRule.importEngineEntities();

    // when
    engineRule.finishAllUserTasks();
    embeddedOptimizeRule.importEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // then
    SearchResponse idsResp = getSearchResponseForAllDocumentsOfType(elasticSearchRule.getEventType());
    assertThat(idsResp.getHits().getTotalHits(), is(6L));
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
    SearchResponse idsResp = getSearchResponseForAllDocumentsOfType(elasticSearchRule.getEventType());
    assertThat(idsResp.getHits().getTotalHits(), is(1L));
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
  public void latestImportIndexAfterRestartOfOptimize() {
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
  public void itIsPossibleToResetTheImportIndex() {
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
    engineRule.deployAndStartProcess(processModel);
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
