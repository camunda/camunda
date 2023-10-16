/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.it;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.schema.indices.DecisionIndex;
import io.camunda.operate.schema.indices.DecisionRequirementsIndex;
import io.camunda.operate.schema.templates.DecisionInstanceTemplate;
import io.camunda.operate.util.OperateZeebeAbstractIT;
import io.camunda.operate.webapp.writer.DecisionWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class DecisionWriterIT extends OperateZeebeAbstractIT {

  @Autowired
  private DecisionWriter decisionWriter;

  @Autowired
  private DecisionRequirementsIndex decisionRequirementsIndex;

  @Autowired
  private DecisionIndex decisionIndex;

  @Autowired
  private DecisionInstanceTemplate decisionInstanceTemplate;

  @Autowired
  private RestHighLevelClient esClient;

  @Before
  public void before() {
    super.before();
  }

  @Test
  public void shouldDeleteDecisionRequirements() throws IOException {

    // given
    long decisionRequirementsKey = deployDecisionRequirements();
    // when
    long deleted = decisionWriter.deleteDecisionRequirements(decisionRequirementsKey);
    // then
    assertThat(deleted).isEqualTo(1);
  }

  @Test
  public void shouldDeleteDecisionDefinitions() throws IOException {

    // given
    long decisionRequirementsKey = deployDecisionRequirements();
    // when
    long deleted = decisionWriter.deleteDecisionDefinitionsFor(decisionRequirementsKey);
    // then
    assertThat(deleted).isEqualTo(2);
  }

  @Test
  public void shouldDeleteDecisionInstances() throws IOException {

    // given
    long decisionRequirementsKey = deployDecisionRequirements();
    deployProcessWithDecision();
    String payload = "{\"amount\": 1200, \"invoiceCategory\": \"Travel Expenses\"}";
    startProcessWithDecision(payload);
    startProcessWithDecision(null);
    waitForDecisionInstances(3);
    // when
    long deleted = decisionWriter.deleteDecisionInstancesFor(decisionRequirementsKey);
    // then
    assertThat(deleted).isEqualTo(3);
  }

  @Test
  public void shouldNotDeleteWhenNothingFound() throws IOException {

    // given
    deployDecisionRequirements();
    long decisionRequirementsKey = 123L;
    // when
    long deleted1 = decisionWriter.deleteDecisionRequirements(decisionRequirementsKey);
    long deleted2 = decisionWriter.deleteDecisionDefinitionsFor(decisionRequirementsKey);
    long deleted3 = decisionWriter.deleteDecisionInstancesFor(decisionRequirementsKey);
    // then
    assertThat(deleted1).isZero();
    assertThat(deleted2).isZero();
    assertThat(deleted3).isZero();
  }

  protected Long deployDecisionRequirements() {
    tester.deployDecision("invoiceBusinessDecisions_v_1.dmn").waitUntil().decisionsAreDeployed(2);
    List<SearchHit> hits = searchAllDocuments(decisionRequirementsIndex.getAlias());
    Map<String, Object> decisionRequirementsDoc = hits.get(0).getSourceAsMap();
    long decisionRequirementsKey = Long.parseLong(decisionRequirementsDoc.get("key").toString());
    return decisionRequirementsKey;
  }

  protected Long deployProcessWithDecision() {
    Long procDefinitionKey = tester.deployProcess("invoice.bpmn").waitUntil().processIsDeployed().getProcessDefinitionKey();
    return procDefinitionKey;
  }

  protected Long startProcessWithDecision(String payload) {
    Long procInstanceKey = tester.startProcessInstance("invoice", payload).waitUntil().processInstanceIsStarted().getProcessInstanceKey();
    return procInstanceKey;
  }

  protected void waitForDecisionInstances(int count) {
    if (count <= 0) {
      throw new IllegalArgumentException("Expected number of decision instances must be positive.");
    }
    tester.waitUntil().decisionInstancesAreCreated(count);
  }

  protected List<SearchHit> searchAllDocuments(String index) {
    SearchRequest searchRequest = new SearchRequest(index).source(new SearchSourceBuilder().size(1000).query(QueryBuilders.matchAllQuery()));
    try {
      SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      return Arrays.asList(response.getHits().getHits());
    } catch (IOException ex) {
      throw new OperateRuntimeException("Search failed for index " + index, ex);
    }
  }
}
