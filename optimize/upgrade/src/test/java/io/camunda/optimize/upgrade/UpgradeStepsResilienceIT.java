/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade;

import static io.camunda.optimize.util.SuppressionConstants.SAME_PARAM_VALUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockserver.verify.VerificationTimes.exactly;

import io.camunda.optimize.service.db.schema.IndexMappingCreator;
import io.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import io.camunda.optimize.upgrade.plan.UpgradePlan;
import io.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import io.camunda.optimize.upgrade.steps.schema.CreateIndexStep;
import io.camunda.optimize.upgrade.steps.schema.DeleteIndexIfExistsStep;
import io.camunda.optimize.upgrade.steps.schema.UpdateIndexStep;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpError;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.MediaType;

public class UpgradeStepsResilienceIT extends AbstractUpgradeIT {

  @SneakyThrows
  @Test
  public void deleteIndexEventuallySucceedsOnPendingSnapshot() {
    // given
    final UpgradePlan upgradePlan = createDeleteIndexPlan();

    final String versionedIndexName =
        getIndexNameService().getOptimizeIndexNameWithVersion(TEST_INDEX_V2);
    final HttpRequest indexDeleteRequest = createIndexDeleteRequest(versionedIndexName);
    dbMockServer
        // respond with this error 2 times, afterwards the request will be forwarded to elastic
        // again
        .when(indexDeleteRequest, Times.exactly(2))
        .respond(createSnapshotInProgressResponse(versionedIndexName));

    // when the upgrade is executed
    final ScheduledExecutorService upgradeExecution = Executors.newSingleThreadScheduledExecutor();
    upgradeProcedure.performUpgrade(upgradePlan);

    // then it eventually completes
    upgradeExecution.shutdown();
    assertThat(upgradeExecution.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

    // and the mocked delete endpoint was called three times in total
    dbMockServer.verify(indexDeleteRequest, exactly(3));
    // and the index is gone
    assertThat(
            databaseIntegrationTestExtension.indexExists(
                getIndexNameService().getOptimizeIndexNameWithVersion(TEST_INDEX_V2)))
        .isFalse();
  }

  @SneakyThrows
  @Test
  public void deleteIndexIndexFailsOnOtherError() {
    // given
    final UpgradePlan upgradePlan = createDeleteIndexPlan();

    final String versionedIndexName =
        getIndexNameService().getOptimizeIndexNameWithVersion(TEST_INDEX_V2);
    final HttpRequest indexDeleteRequest = createIndexDeleteRequest(versionedIndexName);
    dbMockServer
        // respond with a different error
        .when(indexDeleteRequest, Times.exactly(1))
        .error(HttpError.error().withDropConnection(true));

    // when the upgrade is executed it fails
    assertThatThrownBy(() -> upgradeProcedure.performUpgrade(upgradePlan))
        .isInstanceOf(UpgradeRuntimeException.class);

    // and the mocked delete endpoint was called one time in total
    dbMockServer.verify(indexDeleteRequest, exactly(1));
    // and the index is still there
    assertThat(
            databaseIntegrationTestExtension.indexExists(
                getIndexNameService().getOptimizeIndexNameWithVersion(TEST_INDEX_V2)))
        .isTrue();
  }

  @SneakyThrows
  @Test
  public void updateIndexEventuallySucceedsOnPendingSnapshot() {
    // given
    final UpgradePlan upgradePlan = createUpdateIndexPlan();

    final String oldIndexToDeleteName =
        getIndexNameService().getOptimizeIndexNameWithVersion(TEST_INDEX_V1);
    final HttpRequest indexDeleteRequest = createIndexDeleteRequest(oldIndexToDeleteName);
    dbMockServer
        // respond with this error 2 times, afterwards the request will be forwarded to database
        // again
        .when(indexDeleteRequest, Times.exactly(2))
        .respond(createSnapshotInProgressResponse(oldIndexToDeleteName));

    // when the upgrade is executed
    final ScheduledExecutorService upgradeExecution = Executors.newSingleThreadScheduledExecutor();
    upgradeProcedure.performUpgrade(upgradePlan);

    // then it eventually completes
    upgradeExecution.shutdown();
    assertThat(upgradeExecution.awaitTermination(20, TimeUnit.SECONDS)).isTrue();

    // and the mocked delete endpoint was called three times in total
    dbMockServer.verify(indexDeleteRequest, exactly(3));
    // and the old index is gone
    assertThat(
            databaseIntegrationTestExtension.indexExists(
                getIndexNameService().getOptimizeIndexNameWithVersion(TEST_INDEX_V1)))
        .isFalse();
    // and the new index exists
    assertThat(
            databaseIntegrationTestExtension.indexExists(
                getIndexNameService()
                    .getOptimizeIndexNameWithVersion(TEST_INDEX_WITH_UPDATED_MAPPING_V2)))
        .isTrue();
  }

  @Test
  public void updateDeleteIndexFailsOnOtherError() throws IOException {
    // given
    final UpgradePlan upgradePlan = createUpdateIndexPlan();

    final String oldIndexToDeleteName =
        getIndexNameService().getOptimizeIndexNameWithVersion(TEST_INDEX_V1);
    final HttpRequest indexDeleteRequest = createIndexDeleteRequest(oldIndexToDeleteName);
    dbMockServer
        // respond with a different error
        .when(indexDeleteRequest, Times.exactly(1))
        .error(HttpError.error().withDropConnection(true));

    // when the upgrade is executed it fails
    assertThatThrownBy(() -> upgradeProcedure.performUpgrade(upgradePlan))
        .isInstanceOf(UpgradeRuntimeException.class);

    // and the mocked delete endpoint was called one time in total
    dbMockServer.verify(indexDeleteRequest, exactly(1));
    // and the old index is still there
    assertThat(
            databaseIntegrationTestExtension.indexExists(
                getIndexNameService().getOptimizeIndexNameWithVersion(TEST_INDEX_V1)))
        .isTrue();
    // and the new index as well
    assertThat(
            databaseIntegrationTestExtension.indexExists(
                getIndexNameService()
                    .getOptimizeIndexNameWithVersion(TEST_INDEX_WITH_UPDATED_MAPPING_V2)))
        .isTrue();
  }

  private UpgradePlan createDeleteIndexPlan() {
    return UpgradePlanBuilder.createUpgradePlan()
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(applyLookupSkip(new CreateIndexStep(TEST_INDEX_V2)))
        .addUpgradeStep(buildDeleteIndexStep(TEST_INDEX_V2))
        .build();
  }

  private UpgradePlan createUpdateIndexPlan() {
    return UpgradePlanBuilder.createUpgradePlan()
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(applyLookupSkip(new CreateIndexStep(TEST_INDEX_V1)))
        .addUpgradeStep(buildUpdateIndexStep(TEST_INDEX_WITH_UPDATED_MAPPING_V2))
        .build();
  }

  private HttpResponse createSnapshotInProgressResponse(final String indexName) {
    return HttpResponse.response()
        .withStatusCode(Response.Status.BAD_REQUEST.getStatusCode())
        .withBody(getSnapshotInProgressJson(indexName), MediaType.JSON_UTF_8);
  }

  private String getSnapshotInProgressJson(final String indexName) {
    return String.format(
        "{\"error\":{\"root_cause\":[{\"type\":\"snapshot_in_progress_exception\","
            + "\"reason\":\"Cannot delete indices that are"
            + " being snapshotted: [[%s/QlIEbxPkRoOdBHmxU8PPZA]]. Try again after snapshot finishes or "
            + "cancel the currently running snapshot.\"}],\"type\":\"snapshot_in_progress_exception\","
            + "\"reason\":\"Cannot delete indices that are being snapshotted: [[%s/QlIEbxPkRoOdBHmxU8PPZA]]. Try again "
            + "after snapshot finishes or cancel the currently running snapshot.\"},\"status\":400}",
        indexName, indexName);
  }

  private UpdateIndexStep buildUpdateIndexStep(final IndexMappingCreator index) {
    return applyLookupSkip(new UpdateIndexStep(index));
  }

  @SuppressWarnings(SAME_PARAM_VALUE)
  private DeleteIndexIfExistsStep buildDeleteIndexStep(final IndexMappingCreator indexMapping) {
    return applyLookupSkip(
        new DeleteIndexIfExistsStep(indexMapping.getIndexName(), indexMapping.getVersion()));
  }
}
