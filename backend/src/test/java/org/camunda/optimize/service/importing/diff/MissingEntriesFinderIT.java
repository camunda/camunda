package org.camunda.optimize.service.importing.diff;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.test.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.rule.EngineIntegrationRule;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertThat;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/it-applicationContext.xml"})
public class MissingEntriesFinderIT {

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);


  @Before
  public void init() {
    // the id of deleted indices is always stored
    // and the version increment when a document with the
    // same id is added. Therefore, we need to delete the whole
    // index before each test to be sure that all document ids
    // are wiped out.
    elasticSearchRule.deleteOptimizeIndex();
    embeddedOptimizeRule.initializeSchema();
  }


  @Test
  public void onlyNewProcessDefinitionsAreImportedToES() throws Exception {

    // given
    deployImportAndDeployAgainProcess();
    embeddedOptimizeRule.resetImportStartIndex();

    // when I trigger the import a second time
    embeddedOptimizeRule.importEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // then only the new entities are imported
    allDocumentsInElasticsearchAreNew();
  }

  private void allDocumentsInElasticsearchAreNew() {
    QueryBuilder qb = matchAllQuery();

    SearchResponse idsResp = elasticSearchRule.getClient().prepareSearch(elasticSearchRule.getOptimizeIndex())
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
    embeddedOptimizeRule.importEngineEntities();
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

}
