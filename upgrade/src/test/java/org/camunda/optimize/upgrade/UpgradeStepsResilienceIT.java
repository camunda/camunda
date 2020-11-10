/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade;

import com.google.common.collect.Lists;
import lombok.SneakyThrows;
import org.camunda.optimize.service.es.schema.IndexMappingCreator;
import org.camunda.optimize.service.util.configuration.elasticsearch.ElasticsearchConnectionNodeConfiguration;
import org.camunda.optimize.test.it.extension.IntegrationTestConfigurationUtil;
import org.camunda.optimize.test.it.extension.MockServerUtil;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import org.camunda.optimize.upgrade.indexes.UserTestIndex;
import org.camunda.optimize.upgrade.indexes.UserTestUpdatedMappingIndex;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.steps.schema.CreateIndexStep;
import org.camunda.optimize.upgrade.steps.schema.DeleteIndexIfExistsStep;
import org.camunda.optimize.upgrade.steps.schema.UpdateIndexStep;
import org.camunda.optimize.upgrade.util.UpgradeUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpError;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.MediaType;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static javax.ws.rs.HttpMethod.DELETE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder.createDefaultConfiguration;
import static org.camunda.optimize.upgrade.EnvironmentConfigUtil.createEmptyEnvConfig;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.verify.VerificationTimes.exactly;

public class UpgradeStepsResilienceIT extends AbstractUpgradeIT {

  private static final IndexMappingCreator TEST_INDEX_V1 = new UserTestIndex(1);
  private static final IndexMappingCreator TEST_INDEX_V2 = new UserTestIndex(2);
  private static final IndexMappingCreator TEST_INDEX_WITH_UPDATED_MAPPING = new UserTestUpdatedMappingIndex();

  private static final String FROM_VERSION = "2.6.0";
  private static final String TO_VERSION = "2.7.0";

  private ClientAndServer esMockServer;

  @BeforeEach
  @Override
  public void setUp() throws Exception {
    this.configurationService = createDefaultConfiguration();
    final ElasticsearchConnectionNodeConfiguration elasticConfig =
      this.configurationService.getFirstElasticsearchConnectionNode();

    this.esMockServer = createElasticMock(elasticConfig);
    elasticConfig.setHost(MockServerUtil.MOCKSERVER_HOST);
    elasticConfig.setHttpPort(IntegrationTestConfigurationUtil.getElasticsearchMockServerPort());

    this.upgradeDependencies =
      UpgradeUtil.createUpgradeDependenciesWithAConfigurationService(this.configurationService);
    this.objectMapper = upgradeDependencies.getObjectMapper();
    this.prefixAwareClient = upgradeDependencies.getEsClient();
    this.indexNameService = upgradeDependencies.getIndexNameService();
    this.metadataService = upgradeDependencies.getMetadataService();

    cleanAllDataFromElasticsearch();
    createEmptyEnvConfig();
    initSchema(Lists.newArrayList(METADATA_INDEX));
    setMetadataVersion(FROM_VERSION);

    prefixAwareClient.setSnapshotInProgressRetryDelaySeconds(1);
  }

  @AfterEach
  @Override
  public void after() throws Exception {
    super.after();
    this.esMockServer.close();
  }

  @SneakyThrows
  @Test
  public void deleteIndexEventuallySucceedsOnPendingSnapshot() {
    // given
    UpgradePlan upgradePlan = createDeleteIndexPlan();

    final String versionedIndexName = indexNameService.getOptimizeIndexNameWithVersion(TEST_INDEX_V2);
    final HttpRequest indexDeleteRequest = createIndexDeleteRequestMatcher(versionedIndexName);
    esMockServer
      // respond with this error 2 times, afterwards the request will be forwarded to elastic again
      .when(indexDeleteRequest, Times.exactly(2))
      .respond(createSnapshotInProgressResponse(versionedIndexName));

    // when the upgrade is executed
    final ScheduledExecutorService upgradeExecution = Executors.newSingleThreadScheduledExecutor();
    upgradeExecution.execute(upgradePlan::execute);

    // then it eventually completes
    upgradeExecution.shutdown();
    assertThat(upgradeExecution.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

    // and the mocked delete endpoint was called three times in total
    esMockServer.verify(indexDeleteRequest, exactly(3));
    // and the index is gone
    assertThat(prefixAwareClient.exists(TEST_INDEX_V2)).isFalse();
  }

  @SneakyThrows
  @Test
  public void deleteIndexIndexFailsOnOtherError() {
    // given
    UpgradePlan upgradePlan = createDeleteIndexPlan();

    final String versionedIndexName = indexNameService.getOptimizeIndexNameWithVersion(TEST_INDEX_V2);
    final HttpRequest indexDeleteRequest = createIndexDeleteRequestMatcher(versionedIndexName);
    esMockServer
      // respond with a different error
      .when(indexDeleteRequest, Times.exactly(1))
      .error(HttpError.error().withDropConnection(true));

    // when the upgrade is executed it fails
    assertThatThrownBy(upgradePlan::execute).isInstanceOf(UpgradeRuntimeException.class);

    // and the mocked delete endpoint was called one time in total
    esMockServer.verify(indexDeleteRequest, exactly(1));
    // and the index is still there
    assertThat(prefixAwareClient.exists(TEST_INDEX_V2)).isTrue();
  }

  @SneakyThrows
  @Test
  public void updateIndexEventuallySucceedsOnPendingSnapshot() {
    // given
    UpgradePlan upgradePlan = createUpdateIndexPlan();

    final String oldIndexToDeleteName = indexNameService.getOptimizeIndexNameWithVersion(TEST_INDEX_V1);
    final HttpRequest indexDeleteRequest = createIndexDeleteRequestMatcher(oldIndexToDeleteName);
    esMockServer
      // respond with this error 2 times, afterwards the request will be forwarded to elastic again
      .when(indexDeleteRequest, Times.exactly(2))
      .respond(createSnapshotInProgressResponse(oldIndexToDeleteName));

    // when the upgrade is executed
    final ScheduledExecutorService upgradeExecution = Executors.newSingleThreadScheduledExecutor();
    upgradeExecution.execute(upgradePlan::execute);

    // then it eventually completes
    upgradeExecution.shutdown();
    assertThat(upgradeExecution.awaitTermination(20, TimeUnit.SECONDS)).isTrue();

    // and the mocked delete endpoint was called three times in total
    esMockServer.verify(indexDeleteRequest, exactly(3));
    // and the old index is gone
    assertThat(prefixAwareClient.exists(TEST_INDEX_V1)).isFalse();
    // and the new index exists
    assertThat(prefixAwareClient.exists(TEST_INDEX_WITH_UPDATED_MAPPING)).isTrue();
  }

  @Test
  public void updateDeleteIndexFailsOnOtherError() throws IOException {
    // given
    UpgradePlan upgradePlan = createUpdateIndexPlan();

    final String oldIndexToDeleteName = indexNameService.getOptimizeIndexNameWithVersion(TEST_INDEX_V1);
    final HttpRequest indexDeleteRequest = createIndexDeleteRequestMatcher(oldIndexToDeleteName);
    esMockServer
      // respond with a different error
      .when(indexDeleteRequest, Times.exactly(1))
      .error(HttpError.error().withDropConnection(true));

    // when the upgrade is executed it fails
    assertThatThrownBy(upgradePlan::execute).isInstanceOf(UpgradeRuntimeException.class);

    // and the mocked delete endpoint was called one time in total
    esMockServer.verify(indexDeleteRequest, exactly(1));
    // and the old index is still there
    assertThat(prefixAwareClient.exists(TEST_INDEX_V1)).isTrue();
    // and the new index as well
    assertThat(prefixAwareClient.exists(TEST_INDEX_WITH_UPDATED_MAPPING)).isTrue();
  }

  private HttpRequest createIndexDeleteRequestMatcher(final String oldIndexToDeleteName) {
    return request().withPath("/" + oldIndexToDeleteName).withMethod(DELETE);
  }

  private UpgradePlan createDeleteIndexPlan() {
    return UpgradePlanBuilder.createUpgradePlan()
      .addUpgradeDependencies(upgradeDependencies)
      .fromVersion(FROM_VERSION)
      .toVersion(TO_VERSION)
      .addUpgradeStep(new CreateIndexStep(TEST_INDEX_V2))
      .addUpgradeStep(buildDeleteIndexStep(TEST_INDEX_V2))
      .build();
  }

  private UpgradePlan createUpdateIndexPlan() {
    return UpgradePlanBuilder.createUpgradePlan()
      .addUpgradeDependencies(upgradeDependencies)
      .fromVersion(FROM_VERSION)
      .toVersion(TO_VERSION)
      .addUpgradeStep(new CreateIndexStep(TEST_INDEX_V1))
      .addUpgradeStep(buildUpdateIndexStep(TEST_INDEX_WITH_UPDATED_MAPPING))
      .build();
  }

  private ClientAndServer createElasticMock(final ElasticsearchConnectionNodeConfiguration elasticConfig) {
    return MockServerUtil.createProxyMockServer(
      elasticConfig.getHost(),
      elasticConfig.getHttpPort(),
      IntegrationTestConfigurationUtil.getElasticsearchMockServerPort()
    );
  }

  private HttpResponse createSnapshotInProgressResponse(final String indexName) {
    return HttpResponse.response().withStatusCode(Response.Status.BAD_REQUEST.getStatusCode()).withBody(
      getSnapshotInProgressJson(indexName), MediaType.JSON_UTF_8
    );
  }

  private String getSnapshotInProgressJson(final String indexName) {
    return String.format(
      "{\"error\":{\"root_cause\":[{\"type\":\"snapshot_in_progress_exception\"," +
        "\"reason\":\"Cannot delete indices that are" +
        " being snapshotted: [[%s/QlIEbxPkRoOdBHmxU8PPZA]]. Try again after snapshot finishes or " +
        "cancel the currently running snapshot.\"}],\"type\":\"snapshot_in_progress_exception\"," +
        "\"reason\":\"Cannot delete indices that are being snapshotted: [[%s/QlIEbxPkRoOdBHmxU8PPZA]]. Try again " +
        "after snapshot finishes or cancel the currently running snapshot.\"},\"status\":400}",
      indexName, indexName
    );
  }

  private UpdateIndexStep buildUpdateIndexStep(final IndexMappingCreator index) {
    return new UpdateIndexStep(index, null);
  }


  @SuppressWarnings("SameParameterValue")
  private DeleteIndexIfExistsStep buildDeleteIndexStep(final IndexMappingCreator indexMapping) {
    return new DeleteIndexIfExistsStep(indexMapping);
  }

}
