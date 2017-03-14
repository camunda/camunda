package org.camunda.optimize.service.importing;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.HeatMapResponseDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.service.status.ImportProgressReporter;
import org.camunda.optimize.service.util.ConfigurationService;
import org.camunda.optimize.test.AbstractJerseyTest;
import org.camunda.optimize.test.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.rule.EngineIntegrationRule;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
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

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/it-applicationContext.xml"})
public class ImportIT extends AbstractJerseyTest {
  private static final String SUB_PROCESS_ID = "testProcess";
  private static final String CALL_ACTIVITY = "callActivity";
  private static final String TEST_MIPROCESS = "testMIProcess";

  @Autowired
  @Rule
  public EngineIntegrationRule engineRule;

  @Autowired
  @Rule
  public ElasticSearchIntegrationTestRule elasticSearchRule;

  @Autowired
  private ImportScheduler importScheduler;

  @Autowired
  private TransportClient esclient;

  @Autowired
  private ConfigurationService configurationService;

  @Autowired
  private ImportProgressReporter importProgressReporter;

  @Test
  public void allProcessDefinitionFieldDataOfImportIsAvailable() throws Exception {
    //given
    deployAndStartSimpleServiceTask();

    //when
    importScheduler.scheduleProcessEngineImport();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    //then
    allEntriesInElasticsearchHaveAllData(configurationService.getProcessDefinitionType(), 1L);
  }

  @Test
  public void allProcessDefinitionXmlFieldDataOfImportIsAvailable() throws Exception {
    //given
    deployAndStartSimpleServiceTask();

    //when
    importScheduler.scheduleProcessEngineImport();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    //then
    allEntriesInElasticsearchHaveAllData(configurationService.getProcessDefinitionXmlType(), 1L);
  }

  @Test
  public void allEventFieldDataOfImportIsAvailable() throws Exception {
    //given
    deployAndStartSimpleServiceTask();

    //when
    importScheduler.scheduleProcessEngineImport();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    //then
    allEntriesInElasticsearchHaveAllData(configurationService.getEventType(), 3L);
  }

  @Test
  public void importProgressReporterStartAndEndImportState() {
    // when
    deployAndStartSimpleServiceTask();

    // then
    assertThat(importProgressReporter.computeImportProgress(), is(0));

    // when
    importScheduler.scheduleProcessEngineImport();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // then
    assertThat(importProgressReporter.computeImportProgress(), is(100));
  }

  @Test
  public void importProgressReporterItermediateImportState() {
    // given
    deployAndStartSimpleServiceTask();
    importScheduler.scheduleProcessEngineImport();
    deployAndStartSimpleServiceTask();

    // when
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // then
    assertThat(importProgressReporter.computeImportProgress(), is(50));
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

    BpmnModelInstance model = Bpmn.createExecutableProcess(TEST_MIPROCESS)
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
    importScheduler.scheduleProcessEngineImport();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    String token = authenticateAdmin();
    List<ProcessDefinitionOptimizeDto> definitions = target()
        .path(configurationService.getProcessDefinitionEndpoint())
        .request()
        .header(HttpHeaders.AUTHORIZATION,"Bearer " + token)
        .get(new GenericType<List<ProcessDefinitionOptimizeDto>>(){});
    assertThat(definitions.size(),is(2));

    String id = null;
    for (ProcessDefinitionOptimizeDto dto : definitions) {
      if (TEST_MIPROCESS.equals(dto.getKey())) {
        id = dto.getId();
      }
    }
    assertThat(id, is(notNullValue()));


    //when
    HeatMapResponseDto heatMap = target()
        .path(configurationService.getProcessDefinitionEndpoint() + "/" + id + "/" + "heatmap/frequency")
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
    QueryBuilder qb = matchAllQuery();

    SearchResponse idsResp = esclient.prepareSearch(configurationService.getOptimizeIndex())
      .setTypes(elasticsearchType)
      .setQuery(qb)
      .setSize(100)
      .get();

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

  @Override
  protected String getContextLocation() {
    return "classpath:it-applicationContext.xml";
  }

}
