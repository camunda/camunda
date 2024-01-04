/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.cleanup;

import io.github.netmikey.logunit.api.LogCapturer;
import lombok.SneakyThrows;
import org.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
import org.camunda.optimize.service.util.configuration.cleanup.DecisionDefinitionCleanupConfiguration;
import org.camunda.optimize.util.DmnModels;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.db.DatabaseConstants.DECISION_INSTANCE_MULTI_ALIAS;
import static org.camunda.optimize.service.db.schema.index.DecisionInstanceIndex.DECISION_INSTANCE_ID;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

public class EngineDataDecisionCleanupServiceIT extends AbstractCleanupIT {

  @RegisterExtension
  LogCapturer cleanupServiceLogs = LogCapturer.create().captureForType(CleanupService.class);

  @RegisterExtension
  LogCapturer engineDataCleanupLogs = LogCapturer.create().captureForType(EngineDataDecisionCleanupService.class);

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
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();

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
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    assertNoDecisionInstanceDataExists(instanceIdsToCleanup);
    assertDecisionInstancesExistInEs(unaffectedDecisionDefinitionsIds);
  }

  @Test
  @SneakyThrows
  public void testCleanupModeVariables_specificKeyCleanupMode_noInstanceDataExists() {
    // given
    final DecisionDefinitionEngineDto decisionDefinitionEngineDto =
      engineIntegrationExtension.deployDecisionDefinition(DmnModels.createDefaultDmnModel());
    getCleanupConfiguration().getDecisionCleanupConfiguration()
      .getDecisionDefinitionSpecificConfiguration()
      .put(
        decisionDefinitionEngineDto.getKey(),
        new DecisionDefinitionCleanupConfiguration(getCleanupConfiguration().getTtl())
      );

    importAllEngineEntitiesFromScratch();

    // when
    embeddedOptimizeExtension.getCleanupScheduler().runCleanup();
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    engineDataCleanupLogs.assertContains(
      "Finished cleanup on decision instances for decisionDefinitionKey: invoiceClassification, with ttl: P2Y");
  }

  @Test
  @SneakyThrows
  public void testCleanupOnSpecificKeyConfigWithNoMatchingDecisionDefinitionWorksWithLoggedWarning() {
    // given I have a key specific config
    final String configuredKey = "myMistypedKey";
    getCleanupConfiguration().getDecisionCleanupConfiguration()
      .getDecisionDefinitionSpecificConfiguration()
      .put(
        configuredKey,
        new DecisionDefinitionCleanupConfiguration(getCleanupConfiguration().getTtl())
      );
    // and deploy processes with different keys
    deployTwoDecisionInstancesWithEvaluationTimeLessThanTtl();
    final List<String> unaffectedDecisionDefinitionsIds =
      deployTwoDecisionInstancesWithEvaluationTime(OffsetDateTime.now());

    importAllEngineEntitiesFromScratch();

    // when
    embeddedOptimizeExtension.getCleanupScheduler().runCleanup();

    databaseIntegrationTestExtension.refreshAllOptimizeIndices();

    // then data clear up has succeeded as expected
    assertDecisionInstancesExistInEs(unaffectedDecisionDefinitionsIds);
    // and the misconfigured process is logged
    cleanupServiceLogs.assertContains(String.format(
      "History Cleanup Configuration contains definition keys for which there is no "
        + "definition imported yet. The keys without a match in the database are: [%s]", configuredKey));
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

    return databaseIntegrationTestExtension.getOptimizeElasticsearchClient().search(searchRequest);
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
