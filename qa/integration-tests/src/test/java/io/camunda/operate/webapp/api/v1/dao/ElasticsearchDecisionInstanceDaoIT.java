/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.dao;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.schema.templates.DecisionInstanceTemplate;
import io.camunda.operate.util.OperateZeebeIntegrationTest;
import io.camunda.operate.webapp.api.v1.entities.DecisionInstance;
import io.camunda.operate.webapp.api.v1.entities.DecisionInstanceState;
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
import java.util.Arrays;
import java.util.Map;

public class ElasticsearchDecisionInstanceDaoIT extends OperateZeebeIntegrationTest {

  @Autowired
  ElasticsearchDecisionInstanceDao dao;

  @Autowired
  private DecisionInstanceTemplate decisionInstanceTemplate;

  @Autowired
  private RestHighLevelClient esClient;

  private DecisionInstance decisionInstance;
  private String id;
  private Long processDefinitionKey, processInstanceKey;

  @Test
  public void shouldReturnWhenById() throws Exception {
    given(() -> {
      tester.deployDecision("invoiceBusinessDecisions_v_1.dmn").waitUntil().decisionsAreDeployed(2);
      processDefinitionKey = tester.deployProcess("invoice.bpmn").waitUntil().processIsDeployed().getProcessDefinitionKey();
      processInstanceKey = tester.startProcessInstance("invoice").waitUntil().processInstanceIsStarted().getProcessInstanceKey();
      SearchHit[] hits = searchAllDocuments(decisionInstanceTemplate.getAlias());
      Map<String, Object> decisionInstanceDoc = Arrays.stream(hits)
          .filter(x -> x.getSourceAsMap().get("decisionId").toString().equals("invoiceClassification"))
          .findFirst()
          .orElseThrow()
          .getSourceAsMap();
      id = decisionInstanceDoc.get("id").toString();
    });
    when(() -> decisionInstance = dao.byId(id));
    then(() -> {
      assertThat(decisionInstance.getId()).isEqualTo(id);
      assertThat(decisionInstance.getState()).isEqualTo(DecisionInstanceState.FAILED);
      assertThat(decisionInstance.getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
      assertThat(decisionInstance.getProcessInstanceKey()).isEqualTo(processInstanceKey);
      assertThat(decisionInstance.getDecisionId()).isEqualTo("invoiceClassification");
      assertThat(decisionInstance.getDecisionName()).isEqualTo("Invoice Classification");
      assertThat(decisionInstance.getDecisionVersion()).isEqualTo(1);
      assertThat(decisionInstance.getEvaluatedInputs()).isEmpty();
      assertThat(decisionInstance.getEvaluatedOutputs()).isEmpty();
    });
  }

  @Test
  public void shouldReturnEvaluatedWhenById() throws Exception {
    given(() -> {
      String payload = "{\"amount\": 1200, \"invoiceCategory\": \"Travel Expenses\"}";
      tester.deployDecision("invoiceBusinessDecisions_v_1.dmn").waitUntil().decisionsAreDeployed(2);
      processDefinitionKey = tester.deployProcess("invoice.bpmn").waitUntil().processIsDeployed().getProcessDefinitionKey();
      processInstanceKey = tester.startProcessInstance("invoice", payload).waitUntil().processInstanceIsStarted().getProcessInstanceKey();
      SearchHit[] hits = searchAllDocuments(decisionInstanceTemplate.getAlias());
      Map<String, Object> decisionInstanceDoc = Arrays.stream(hits)
          .filter(x -> x.getSourceAsMap().get("decisionId").toString().equals("invoiceClassification"))
          .findFirst()
          .orElseThrow()
          .getSourceAsMap();
      id = decisionInstanceDoc.get("id").toString();
    });
    when(() -> decisionInstance = dao.byId(id));
    then(() -> {
      assertThat(decisionInstance.getId()).isEqualTo(id);
      assertThat(decisionInstance.getState()).isEqualTo(DecisionInstanceState.EVALUATED);
      assertThat(decisionInstance.getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
      assertThat(decisionInstance.getProcessInstanceKey()).isEqualTo(processInstanceKey);
      assertThat(decisionInstance.getDecisionId()).isEqualTo("invoiceClassification");
      assertThat(decisionInstance.getDecisionName()).isEqualTo("Invoice Classification");
      assertThat(decisionInstance.getDecisionVersion()).isEqualTo(1);
      assertThat(decisionInstance.getEvaluatedInputs().size()).isEqualTo(2);
      assertThat(decisionInstance.getEvaluatedInputs()).extracting("value").containsExactly("1200", "\"Travel Expenses\"");
      assertThat(decisionInstance.getEvaluatedOutputs().size()).isEqualTo(1);
      assertThat(decisionInstance.getEvaluatedOutputs()).extracting("value").containsExactly("\"day-to-day expense\"");
    });
  }

  @Test(expected = ResourceNotFoundException.class)
  public void shouldThrowWhenByIdNotExists() throws Exception {
    given(() -> {
    });
    when(() -> dao.byId("-27"));
  }

  @Test(expected = ServerException.class)
  public void shouldThrowWhenByIdFails() throws Exception {
    given(() -> {
    });
    when(() -> dao.byId(null));
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
