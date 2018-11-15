package org.camunda.optimize.service.importing;

import org.camunda.bpm.model.dmn.Dmn;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.optimize.dto.engine.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.importing.DecisionInstanceDto;
import org.camunda.optimize.rest.engine.dto.DeploymentDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineDatabaseRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map.Entry;

import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;


public class DecisionImportIT {

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  public EngineDatabaseRule engineDatabaseRule = new EngineDatabaseRule();

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule).around(engineDatabaseRule);

  @Test
  public void allDecisionDefinitionFieldDataOfImportIsAvailable() {
    //given
    deployDecisionDefinition();
    deployDecisionDefinition();

    //when
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    //then
    allEntriesInElasticsearchHaveAllDataWithCount(ElasticsearchConstants.DECISION_DEFINITION_TYPE, 2L);
  }

  @Test
  public void directlyExecutedDecisionInstanceFieldDataOfImportIsAvailable() throws IOException {
    //given
    deployAndStartDecisionDefinition();

    //when
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    //then
    final SearchResponse idsResp = getSearchResponseForAllDocumentsOfType(ElasticsearchConstants.DECISION_INSTANCE_TYPE);
    assertThat(idsResp.getHits().getTotalHits(), is(1L));

    final DecisionInstanceDto dto = parseToDto(idsResp.getHits().getHits()[0], DecisionInstanceDto.class);
    assertThat(dto.getProcessDefinitionId(), is(nullValue()));
    assertThat(dto.getProcessDefinitionKey(), is(nullValue()));
    assertThat(dto.getDecisionDefinitionId(), is(notNullValue()));
    assertThat(dto.getDecisionDefinitionKey(), is(notNullValue()));
    assertThat(dto.getEvaluationTime(), is(notNullValue()));
    assertThat(dto.getProcessInstanceId(), is(nullValue()));
    assertThat(dto.getRootProcessInstanceId(), is(nullValue()));
    assertThat(dto.getActivityId(), is(nullValue()));
    assertThat(dto.getCollectResultValue(), is(nullValue()));
    assertThat(dto.getRootDecisionInstanceId(), is(nullValue()));
    assertThat(dto.getInputs().size(), is(2));
    dto.getInputs().forEach(inputInstanceDto -> {
      assertThat(inputInstanceDto.getId(), is(notNullValue()));
      assertThat(inputInstanceDto.getClauseId(), is(notNullValue()));
      assertThat(inputInstanceDto.getClauseName(), is(notNullValue()));
      assertThat(inputInstanceDto.getType(), is(notNullValue()));
      assertThat(inputInstanceDto.getValue(), is(notNullValue()));
    });
    assertThat(dto.getOutputs().size(), is(1));
    dto.getOutputs().forEach(outputInstanceDto -> {
      assertThat(outputInstanceDto.getId(), is(notNullValue()));
      assertThat(outputInstanceDto.getClauseId(), is(notNullValue()));
      assertThat(outputInstanceDto.getClauseName(), is(notNullValue()));
      assertThat(outputInstanceDto.getType(), is(notNullValue()));
      assertThat(outputInstanceDto.getValue(), is(notNullValue()));
      assertThat(outputInstanceDto.getRuleId(), is(notNullValue()));
      assertThat(outputInstanceDto.getRuleOrder(), is(notNullValue()));
    });
    assertThat(dto.getEngine(), is(notNullValue()));
  }

  private <T> T parseToDto(final SearchHit searchHit, Class<T> dtoClass) throws IOException {
    return elasticSearchRule.getObjectMapper().readValue(searchHit.getSourceAsString(), dtoClass);
  }

  private DecisionDefinitionEngineDto deployDecisionDefinition() {
    final DmnModelInstance dmnModelInstance = Dmn.readModelFromStream(
      getClass().getClassLoader().getResourceAsStream("dmn/invoiceBusinessDecision.xml")
    );

    final DeploymentDto deploymentDto = engineRule.deployDecisionDefinition(dmnModelInstance);
    return engineRule.getDecisionDefinitionByDeployment(deploymentDto);
  }

  private DecisionDefinitionEngineDto deployAndStartDecisionDefinition() {
    final DecisionDefinitionEngineDto decisionDefinitionEngineDto = deployDecisionDefinition();
    engineRule.startDecisionInstance(
      decisionDefinitionEngineDto.getId(),
      new HashMap<String, Object>() {{
        put("amount", 200);
        put("invoiceCategory", "Misc");
      }}
    );
    return decisionDefinitionEngineDto;
  }

  private void allEntriesInElasticsearchHaveAllDataWithCount(final String elasticsearchType,
                                                             final long count) {
    SearchResponse idsResp = getSearchResponseForAllDocumentsOfType(elasticsearchType);

    assertThat(idsResp.getHits().getTotalHits(), is(count));
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

  private SearchResponse getSearchResponseForAllDocumentsOfType(final String elasticsearchType) {
    QueryBuilder qb = matchAllQuery();

    return elasticSearchRule.getClient().prepareSearch(elasticSearchRule.getOptimizeIndex(elasticsearchType))
      .setTypes(elasticsearchType)
      .setQuery(qb)
      .setSize(100)
      .get();
  }

}
