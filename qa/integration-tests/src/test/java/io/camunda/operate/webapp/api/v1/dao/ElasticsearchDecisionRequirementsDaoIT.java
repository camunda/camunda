/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.dao;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.schema.indices.DecisionRequirementsIndex;
import io.camunda.operate.util.OperateZeebeIntegrationTest;
import io.camunda.operate.webapp.api.v1.entities.DecisionRequirements;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.Map;

public class ElasticsearchDecisionRequirementsDaoIT extends OperateZeebeIntegrationTest {

  @Autowired
  ElasticsearchDecisionRequirementsDao dao;

  @Autowired
  private DecisionRequirementsIndex decisionRequirementsIndex;

  @Autowired
  private RestHighLevelClient esClient;

  private DecisionRequirements decisionRequirements;
  private Long key;

  @Test
  public void shouldReturnWhenByKey() throws Exception {
    given(() -> {
      tester.deployDecision("invoiceBusinessDecisions_v_1.dmn").waitUntil().decisionsAreDeployed(2);
      SearchHit[] hits = searchAllDocuments(decisionRequirementsIndex.getAlias());
      Map<String, Object> decisionRequirementsDoc = hits[0].getSourceAsMap();
      key = Long.parseLong(decisionRequirementsDoc.get("key").toString());
    });
    when(() -> decisionRequirements = dao.byKey(key));
    then(() -> {
      assertThat(decisionRequirements.getKey()).isEqualTo(key);
      assertThat(decisionRequirements.getDecisionRequirementsId()).isEqualTo("invoiceBusinessDecisions");
    });
  }

  @Test(expected = ResourceNotFoundException.class)
  public void showThrowWhenByKeyNotExists() throws Exception {
    given(() -> {
    });
    when(() -> dao.byKey(-27L));
  }

  @Test(expected = ServerException.class)
  public void shouldThrowWhenByKeyFails() throws Exception {
    given(() -> {
    });
    when(() -> dao.byKey(null));
  }

  protected SearchHit[] searchAllDocuments(String index) {
    SearchRequest searchRequest = new SearchRequest(index).source(new SearchSourceBuilder().query(QueryBuilders.matchAllQuery()));
    try {
      SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      return response.getHits().getHits();
    } catch (IOException ex) {
      throw new OperateRuntimeException("Search failed for index " + index, ex);
    }
  }

  protected void given(Runnable conditions) throws Exception {
    conditions.run();
  }

  protected void when(Runnable actions) throws Exception {
    actions.run();
  }

  protected void then(Runnable asserts) throws Exception {
    asserts.run();
  }
}
