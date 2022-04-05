/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.es;

import static io.camunda.operate.util.TestUtil.createDecisionInstanceEntity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.entities.dmn.DecisionInstanceEntity;
import io.camunda.operate.schema.templates.DecisionInstanceTemplate;
import io.camunda.operate.util.ElasticsearchTestRule;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.util.OperateIntegrationTest;
import java.util.List;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class DMNQueryIT extends OperateIntegrationTest {

  @Rule
  public ElasticsearchTestRule elasticsearchTestRule = new ElasticsearchTestRule();

  @Autowired
  private DecisionInstanceTemplate decisionInstanceTemplate;

  @Autowired
  private RestHighLevelClient esClient;

  @Autowired
  private ObjectMapper objectMapper;

  @Test
  public void testReadWriteDecisions() throws Exception {
    createData();

    final SearchRequest searchRequest = new SearchRequest(decisionInstanceTemplate.getFullQualifiedName())
        .source(new SearchSourceBuilder()
        .query(matchAllQuery()));
    final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
    final List<DecisionInstanceEntity> decisionInstances = ElasticsearchUtil
        .mapSearchHits(response.getHits().getHits(), objectMapper,
            DecisionInstanceEntity.class);
    assertThat(decisionInstances).hasSize(2);
    assertThat(decisionInstances.get(0).getEvaluatedInputs()).hasSize(2);
    assertThat(decisionInstances.get(0).getEvaluatedOutputs()).hasSize(2);
  }

  protected void createData() {
    elasticsearchTestRule.persistNew(createDecisionInstanceEntity(), createDecisionInstanceEntity());
  }

}
