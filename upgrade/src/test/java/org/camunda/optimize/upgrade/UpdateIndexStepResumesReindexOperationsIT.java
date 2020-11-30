/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade;

import com.google.common.collect.ImmutableList;
import io.github.netmikey.logunit.api.LogCapturer;
import org.camunda.optimize.test.util.DateCreationFreezer;
import org.camunda.optimize.upgrade.es.SchemaUpgradeClient;
import org.camunda.optimize.upgrade.es.index.UpdateLogEntryIndex;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.service.UpgradeStepLogEntryDto;
import org.camunda.optimize.upgrade.steps.schema.CreateIndexStep;
import org.camunda.optimize.upgrade.steps.schema.UpdateIndexStep;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.common.xcontent.XContentType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpError;
import org.mockserver.model.HttpOverrideForwardedRequest;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.JsonBody;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;

import static javax.ws.rs.HttpMethod.GET;
import static javax.ws.rs.HttpMethod.POST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.verify.VerificationTimes.atLeast;
import static org.mockserver.verify.VerificationTimes.exactly;

public class UpdateIndexStepResumesReindexOperationsIT extends AbstractUpgradeIT {
  @RegisterExtension
  protected final LogCapturer schemaUpdateClientLogs = LogCapturer.create().captureForType(SchemaUpgradeClient.class);

  @Test
  public void detectRunningReindexAndWaitForIt() throws IOException {
    // given a prepared index with some data in it
    upgradeProcedure.performUpgrade(
      UpgradePlanBuilder.createUpgradePlan()
        .fromVersion(FROM_VERSION)
        .toVersion(INTERMEDIATE_VERSION)
        .addUpgradeSteps(ImmutableList.of(new CreateIndexStep(TEST_INDEX_V1)))
        .build()
    );

    insertTestDocuments(5);

    // and the update was run
    final UpdateIndexStep upgradeStep = new UpdateIndexStep(TEST_INDEX_V2);
    final UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .fromVersion(INTERMEDIATE_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(upgradeStep)
        .build();

    // with a throttled reindex (so it is still pending later)
    final HttpRequest reindexRequest = forwardThrottledReindexRequestWithOneDocPerSecond(
      getVersionedIndexName(TEST_INDEX_V1.getIndexName(), 1),
      getVersionedIndexName(TEST_INDEX_V2.getIndexName(), 2)
    );

    // and getting the reindex status immediately failed and aborted the upgrade
    final HttpRequest getReindexStatusRequest = request().withPath("/_tasks/.*").withMethod(GET);
    esMockServer
      .when(getReindexStatusRequest, Times.exactly(1))
      .error(HttpError.error().withDropConnection(true));
    assertThatThrownBy(() -> upgradeProcedure.performUpgrade(upgradePlan)).isInstanceOf(UpgradeRuntimeException.class);
    esMockServer.verify(reindexRequest, atLeast(1));
    esMockServer.verify(getReindexStatusRequest, exactly(1));

    // when it is retried and any new reindex operation would get rejected
    esMockServer
      .when(reindexRequest, Times.unlimited())
      .error(HttpError.error().withDropConnection(true));
    final OffsetDateTime frozenDate = DateCreationFreezer.dateFreezer().freezeDateAndReturn();
    upgradeProcedure.performUpgrade(upgradePlan);

    // then it succeeds
    final List<UpgradeStepLogEntryDto> updateLogEntries = getAllDocumentsOfIndexAs(
      UpdateLogEntryIndex.INDEX_NAME, UpgradeStepLogEntryDto.class
    );
    assertThat(updateLogEntries)
      .contains(
        UpgradeStepLogEntryDto.builder()
          .indexName(upgradeStep.getIndex().getIndexName())
          .optimizeVersion(TO_VERSION)
          .stepNumber(1)
          .stepType(upgradeStep.getType())
          .appliedDate(frozenDate.toInstant())
          .build()
      );

    schemaUpdateClientLogs.assertContains("Found pending reindex task with id");
    schemaUpdateClientLogs.assertContains(
      "from index [optimize-users_v1] to [optimize-users_v2], will wait for it to finish."
    );
  }

  @Test
  public void detectFinishedReindexAndSkipIt() throws IOException {
    // given a prepared index with some data in it
    upgradeProcedure.performUpgrade(
      UpgradePlanBuilder.createUpgradePlan()
        .fromVersion(FROM_VERSION)
        .toVersion(INTERMEDIATE_VERSION)
        .addUpgradeSteps(ImmutableList.of(new CreateIndexStep(TEST_INDEX_V1)))
        .build()
    );

    insertTestDocuments(5);

    // and the update was run
    final UpdateIndexStep upgradeStep = new UpdateIndexStep(TEST_INDEX_V2);
    final UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .fromVersion(INTERMEDIATE_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(upgradeStep)
        .build();

    // and getting the reindex status immediately failed and aborted the upgrade
    final HttpRequest getReindexStatusRequest = request().withPath("/_tasks/.*").withMethod(GET);
    esMockServer
      .when(getReindexStatusRequest, Times.exactly(1))
      .error(HttpError.error().withDropConnection(true));
    assertThatThrownBy(() -> upgradeProcedure.performUpgrade(upgradePlan)).isInstanceOf(UpgradeRuntimeException.class);
    esMockServer.verify(createReindexRequestMatcher(), atLeast(1));
    esMockServer.verify(getReindexStatusRequest, exactly(1));

    // when it is retried and any new reindex operation would get rejected
    esMockServer
      .when(createReindexRequestMatcher(), Times.unlimited())
      .error(HttpError.error().withDropConnection(true));
    final OffsetDateTime frozenDate = DateCreationFreezer.dateFreezer().freezeDateAndReturn();
    upgradeProcedure.performUpgrade(upgradePlan);

    // then it succeeds
    final List<UpgradeStepLogEntryDto> updateLogEntries = getAllDocumentsOfIndexAs(
      UpdateLogEntryIndex.INDEX_NAME, UpgradeStepLogEntryDto.class
    );
    assertThat(updateLogEntries)
      .contains(
        UpgradeStepLogEntryDto.builder()
          .indexName(upgradeStep.getIndex().getIndexName())
          .optimizeVersion(TO_VERSION)
          .stepNumber(1)
          .stepType(upgradeStep.getType())
          .appliedDate(frozenDate.toInstant())
          .build()
      );

    schemaUpdateClientLogs.assertContains(
      "Found that index [optimize-users_v2] already contains " +
        "the same amount of documents as [optimize-users_v1], will skip reindex."
    );
  }

  private void insertTestDocuments(final int amount) throws IOException {
    final String indexName = TEST_INDEX_V1.getIndexName();
    final BulkRequest bulkRequest = new BulkRequest();
    for (int i = 0; i < amount; i++) {
      bulkRequest.add(
        new IndexRequest(indexName)
          .source(String.format("{\"password\" : \"admin\",\"username\" : \"admin%d\"}", i), XContentType.JSON)
      );
    }
    prefixAwareClient.bulk(bulkRequest, RequestOptions.DEFAULT);
    prefixAwareClient.refresh(new RefreshRequest(indexName));
  }

  private HttpRequest forwardThrottledReindexRequestWithOneDocPerSecond(final String sourceIndexName,
                                                                        final String targetIndexName) {
    final HttpRequest reindexRequest = createReindexRequestMatcher();
    esMockServer
      .when(reindexRequest, Times.exactly(1))
      .forward(
        HttpOverrideForwardedRequest.forwardOverriddenRequest(
          createReindexRequestMatcher()
            .withBody(JsonBody.json(
              "{" +
                "\"source\": {\"index\": \"" + sourceIndexName + "\", \"size\": \"1\"}," +
                "\"dest\": {\"index\": \"" + targetIndexName + "\"}" +
                "}"
            ))
            .withQueryStringParameter("requests_per_second", "1.0")
        )
      );
    return reindexRequest;
  }

  private HttpRequest createReindexRequestMatcher() {
    return request().withPath("/_reindex").withMethod(POST);
  }

}
