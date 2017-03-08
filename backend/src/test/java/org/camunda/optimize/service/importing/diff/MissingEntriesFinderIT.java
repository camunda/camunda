package org.camunda.optimize.service.importing.diff;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.service.importing.ImportScheduler;
import org.camunda.optimize.service.importing.impl.ActivityImportService;
import org.camunda.optimize.service.importing.impl.ProcessDefinitionImportService;
import org.camunda.optimize.service.importing.impl.ProcessDefinitionXmlImportService;
import org.camunda.optimize.service.util.ConfigurationService;
import org.camunda.optimize.test.AbstractJerseyTest;
import org.camunda.optimize.test.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.rule.EngineIntegrationRule;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertThat;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/it-applicationContext.xml"})
public class MissingEntriesFinderIT extends AbstractJerseyTest {

  @Autowired
  @Rule
  public EngineIntegrationRule engineRule;

  @Autowired
  @Rule
  public ElasticSearchIntegrationTestRule elasticSearchRule;

  @Autowired
  private ProcessDefinitionImportService processDefinitionImportService;

  @Autowired
  private ActivityImportService activityImportService;

  @Autowired
  private ProcessDefinitionXmlImportService processDefinitionXmlImportService;

  @Autowired
  private ImportScheduler importScheduler;

  @Autowired
  private TransportClient esclient;

  @Before
  public void init() {
    // the id of deleted indices is always stored
    // and the version increment when a document with the
    // same id is added. Therefore, we need to delete the whole
    // index before each test to be sure that all document ids
    // are wiped out.
    elasticSearchRule.deleteAndInitializeOptimizeIndex();
  }

  @Autowired
  private ConfigurationService configurationService;

  @Test
  public void onlyNewProcessDefinitionsAreImportedToES() throws Exception {

    // given
    deployImportAndDeployAgainProcess();
    processDefinitionImportService.resetImportStartIndex();

    // when I trigger the import a second time
    importScheduler.scheduleProcessEngineImport();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // then only the new entities are imported
    allDocumentsInElasticsearchAreNew();
  }

  @Test
  public void onlyNewActivitiesAreImportedToES() throws Exception {
    // given
    deployImportAndDeployAgainProcess();
    activityImportService.resetImportStartIndex();

    // when I trigger the import a second time
    importScheduler.scheduleProcessEngineImport();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // then only the new entities are imported
    allDocumentsInElasticsearchAreNew();
  }

  @Test
  public void onlyNewProcessDefinitionXmlsAreImportedToES() throws Exception {
    // given
    deployImportAndDeployAgainProcess();
    processDefinitionXmlImportService.resetImportStartIndex();

    // when I trigger the import a second time
    importScheduler.scheduleProcessEngineImport();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // then only the new entities are imported
    allDocumentsInElasticsearchAreNew();
  }

  private void allDocumentsInElasticsearchAreNew() {
    QueryBuilder qb = matchAllQuery();

    SearchResponse idsResp = esclient.prepareSearch(configurationService.getOptimizeIndex())
      .setQuery(qb)
      .setVersion(true)
      .setFetchSource(false)
      .setSize(100)
      .get();

    assertThat(idsResp.getHits().getTotalHits(), greaterThan(0L));
    for (SearchHit searchHitFields : idsResp.getHits().getHits()) {
      assertThat(searchHitFields.getVersion(), is(1L));
    }
  }

  private void deployImportAndDeployAgainProcess() throws InterruptedException {

    deployAndStartSimpleServiceTask();
    importScheduler.scheduleProcessEngineImport();

    // refresh so it is possible to retrieve the index
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    deployAndStartSimpleServiceTask();
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

  @Override
  protected String getContextLocation() {
    return "classpath:it-applicationContext.xml";
  }
}
