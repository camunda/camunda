/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.cleanup;

import lombok.SneakyThrows;
import org.apache.commons.collections.ListUtils;
import org.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
import org.camunda.optimize.service.util.configuration.cleanup.DecisionDefinitionCleanupConfiguration;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex.DECISION_INSTANCE_ID;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_INSTANCE_MULTI_ALIAS;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

public class EngineDataDecisionCleanupServiceIT extends AbstractCleanupIT {

  @BeforeEach
  public void enableCamundaCleanup() {
    embeddedOptimizeExtension.getConfigurationService()
      .getCleanupServiceConfiguration()
      .getDecisionCleanupConfiguration()
      .setEnabled(true);
  }

  @Test
  @SneakyThrows
  public void testCleanupWithDecisionInstanceDelete() {
    // given
    final List<String> decisionInstanceIds = deployTwoDecisionInstancesWithEvaluationTimeLessThanTtl();

    importAllEngineEntitiesFromScratch();

    // when
    embeddedOptimizeExtension.getCleanupScheduler().runCleanup();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    assertNoDecisionInstanceDataExists(decisionInstanceIds);
  }

  @Test
  @SneakyThrows
  public void testCleanupWithDecisionInstanceDeleteVerifyThatNewOnesAreUnaffected() {
    // given
    final List<String> instanceIdsToCleanup = deployTwoDecisionInstancesWithEvaluationTimeLessThanTtl();
    final List<String> unaffectedDecisionDefinitionsIds = deployTwoDecisionInstancesWithEvaluationTime(
      OffsetDateTime.now()
    );

    importAllEngineEntitiesFromScratch();

    // when
    embeddedOptimizeExtension.getCleanupScheduler().runCleanup();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    assertNoDecisionInstanceDataExists(instanceIdsToCleanup);
    assertDecisionInstancesExistInEs(unaffectedDecisionDefinitionsIds);
  }

  @Test
  @SneakyThrows
  public void testFailCleanupOnSpecificKeyConfigWithNoMatchingDecisionDefinitionNoInstancesCleaned() {
    // given I have a key specific config
    final String configuredKey = "myMistypedKey";
    getCleanupConfiguration().getDecisionCleanupConfiguration()
      .getDecisionDefinitionSpecificConfiguration()
      .put(
        configuredKey,
        new DecisionDefinitionCleanupConfiguration(getCleanupConfiguration().getTtl())
      );
    // and deploy processes with different keys
    final List<String> decisionDefinitionsWithEvaluationTimeLessThanTtl =
      deployTwoDecisionInstancesWithEvaluationTimeLessThanTtl();
    final List<String> unaffectedDecisionDefinitionsIds =
      deployTwoDecisionInstancesWithEvaluationTime(OffsetDateTime.now());

    importAllEngineEntitiesFromScratch();

    // when
    embeddedOptimizeExtension.getCleanupScheduler().runCleanup();

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // all data is still there
    assertDecisionInstancesExistInEs(
      ListUtils.union(decisionDefinitionsWithEvaluationTimeLessThanTtl, unaffectedDecisionDefinitionsIds)
    );
  }

  @SneakyThrows
  protected void assertNoDecisionInstanceDataExists(final List<String> decisionInstanceIds) {
    assertThat(getDecisionInstancesById(decisionInstanceIds).getHits().getTotalHits().value).isZero();
  }

  protected void assertDecisionInstancesExistInEs(List<String> decisionInstanceIds) throws IOException {
    SearchResponse idsResp = getDecisionInstancesById(decisionInstanceIds);
    assertThat(idsResp.getHits().getTotalHits().value).isEqualTo(decisionInstanceIds.size());
  }

  protected SearchResponse getDecisionInstancesById(List<String> decisionInstanceIds) throws IOException {
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(termsQuery(DECISION_INSTANCE_ID, decisionInstanceIds))
      .trackTotalHits(true)
      .size(100);

    SearchRequest searchRequest = new SearchRequest()
      .indices(DECISION_INSTANCE_MULTI_ALIAS)
      .source(searchSourceBuilder);

    return elasticSearchIntegrationTestExtension.getOptimizeElasticClient().search(searchRequest);
  }

  @SneakyThrows
  protected List<String> deployTwoDecisionInstancesWithEvaluationTimeLessThanTtl() {
    return deployTwoDecisionInstancesWithEvaluationTime(getEndTimeLessThanGlobalTtl());
  }

  protected List<String> deployTwoDecisionInstancesWithEvaluationTime(OffsetDateTime evaluationTime) throws
                                                                                                     SQLException {
    final DecisionDefinitionEngineDto decisionDefinitionEngineDto =
      engineIntegrationExtension.deployDecisionDefinition();

    OffsetDateTime lastEvaluationDateFilter = OffsetDateTime.now();
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionEngineDto.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionEngineDto.getId());

    engineDatabaseExtension.changeDecisionInstanceEvaluationDate(lastEvaluationDateFilter, evaluationTime);

    return engineDatabaseExtension.getDecisionInstanceIdsWithEvaluationDateEqualTo(evaluationTime);
  }

}
