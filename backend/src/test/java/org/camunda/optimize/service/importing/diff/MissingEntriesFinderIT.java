package org.camunda.optimize.service.importing.diff;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.service.exceptions.OptimizeException;
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
@ContextConfiguration(locations = {"/it/it-applicationContext.xml"})
public class MissingEntriesFinderIT {

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  @Test
  public void onlyNewProcessDefinitionsAreImportedToES() throws Exception {

    // given
    deployImportAndDeployAgainProcess();
    embeddedOptimizeRule.resetImportStartIndexes();

    // when I trigger the import a second time
    embeddedOptimizeRule.importEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // then only the new entities are imported
    allDocumentsInElasticsearchAreNew();
  }

  private void allDocumentsInElasticsearchAreNew() {
    QueryBuilder qb = matchAllQuery();

    SearchResponse idsResp = elasticSearchRule.getClient().prepareSearch(elasticSearchRule.getOptimizeIndex())
      .setTypes()
      .setQuery(qb)
      .setVersion(true)
      .setFetchSource(false)
      .setSize(100)
      .get();

    assertThat(idsResp.getHits().getTotalHits(), greaterThan(0L));
    for (SearchHit searchHitFields : idsResp.getHits().getHits()) {
      if (isOfImportEntityType(searchHitFields.getType())) {
        assertThat(searchHitFields.getVersion(), is(1L));
      }
    }
  }

  private boolean isOfImportEntityType(String elasticsearchType) {
    return elasticsearchType.equals(elasticSearchRule.getProcessDefinitionType()) ||
      elasticsearchType.equals(elasticSearchRule.getProcessDefinitionType()) ||
      elasticsearchType.equals(elasticSearchRule.getProcessDefinitionXmlType());
  }

  private void deployImportAndDeployAgainProcess() throws InterruptedException, OptimizeException {
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
