/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade;

import com.google.common.collect.ImmutableList;
import io.github.netmikey.logunit.api.LogCapturer;
import org.camunda.optimize.service.es.schema.IndexMappingCreator;
import org.camunda.optimize.service.es.writer.ElasticsearchWriterUtil;
import org.camunda.optimize.test.util.DateCreationFreezer;
import org.camunda.optimize.upgrade.es.SchemaUpgradeClient;
import org.camunda.optimize.upgrade.es.index.UpdateLogEntryIndex;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.service.UpgradeStepLogEntryDto;
import org.camunda.optimize.upgrade.steps.schema.CreateIndexStep;
import org.camunda.optimize.upgrade.steps.schema.UpdateIndexStep;
import org.elasticsearch.client.indices.GetIndexResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockserver.matchers.MatchType;
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
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.INDEX_SUFFIX_PRE_ROLLOVER;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.verify.VerificationTimes.atLeast;
import static org.mockserver.verify.VerificationTimes.exactly;

public class UpdateIndexStepResumesReindexOperationsIT extends AbstractUpgradeIT {

  private static final String NEWEST_INDEX_SUFFIX = "-000002";

  @RegisterExtension
  protected final LogCapturer schemaUpdateClientLogs = LogCapturer.create().captureForType(SchemaUpgradeClient.class);

  @Test
  public void singleIndexDetectRunningReindexAndWaitForIt() throws IOException {
    // given a prepared index with some data in it
    createIndex(TEST_INDEX_V1);
    insertTestDocuments(5);
    // and the update was run
    final UpdateIndexStep upgradeStep = new UpdateIndexStep(TEST_INDEX_V2);
    final UpgradePlan upgradePlan = createUpdatePlan(upgradeStep);
    // with a throttled reindex (so it is still pending later)
    final HttpRequest reindexRequest = forwardThrottledReindexRequestWithOneDocPerSecond(
      getVersionedIndexName(TEST_INDEX_V1.getIndexName(), 1),
      getVersionedIndexName(TEST_INDEX_V2.getIndexName(), 2)
    );
    // and getting the reindex status immediately failed and aborted the upgrade
    performUpgradeAndLetReindexStatusCheckFail(upgradePlan, reindexRequest);

    // when it is retried and any new reindex operation would get rejected
    esMockServer
      .when(reindexRequest, Times.unlimited())
      .error(HttpError.error().withDropConnection(true));
    final OffsetDateTime frozenDate = DateCreationFreezer.dateFreezer().freezeDateAndReturn();
    upgradeProcedure.performUpgrade(upgradePlan);

    // then it succeeds
    assertUpdateLogIsComplete(upgradeStep, frozenDate);

    final GetIndexResponse newIndex = getIndicesForMapping(TEST_INDEX_V2);
    assertThat(newIndex.getAliases()).hasSize(1);
    assertThat(newIndex.getAliases().get(getVersionedIndexName(TEST_INDEX_V2.getIndexName(), 2)))
      .singleElement()
      .satisfies(aliasMetadata -> assertThat(aliasMetadata.writeIndex()).isTrue());

    schemaUpdateClientLogs.assertContains("Found pending reindex task with id");
    schemaUpdateClientLogs.assertContains(
      "from index [optimize-users_v1] to [optimize-users_v2], will wait for it to finish."
    );
  }

  @Test
  public void templatedIndexDetectRunningReindexAndWaitForIt() throws IOException {
    // given a prepared index with some data in it
    createIndex(TEST_INDEX_WITH_TEMPLATE_V1);
    insertTestDocuments(5);
    // and the update was run
    final UpdateIndexStep upgradeStep = new UpdateIndexStep(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2);
    final UpgradePlan upgradePlan = createUpdatePlan(upgradeStep);
    // with a throttled reindex (so it is still pending later)
    final HttpRequest firstIndexReindexRequest = forwardThrottledReindexRequestWithOneDocPerSecond(
      getVersionedIndexName(TEST_INDEX_WITH_TEMPLATE_V1.getIndexName(), 1) + INDEX_SUFFIX_PRE_ROLLOVER,
      getVersionedIndexName(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2.getIndexName(), 2) + INDEX_SUFFIX_PRE_ROLLOVER
    );
    // and getting the reindex status immediately failed and aborted the upgrade
    performUpgradeAndLetReindexStatusCheckFail(upgradePlan, firstIndexReindexRequest);

    // when it is retried and any new reindex operation would get rejected
    esMockServer
      .when(firstIndexReindexRequest, Times.unlimited())
      .error(HttpError.error().withDropConnection(true));
    final OffsetDateTime frozenDate = DateCreationFreezer.dateFreezer().freezeDateAndReturn();
    upgradeProcedure.performUpgrade(upgradePlan);

    // then it succeeds
    assertUpdateLogIsComplete(upgradeStep, frozenDate);

    final GetIndexResponse newIndex = getIndicesForMapping(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2);
    assertThat(newIndex.getAliases().get(
      getVersionedIndexName(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2.getIndexName(), 2) + INDEX_SUFFIX_PRE_ROLLOVER
    ))
      .singleElement()
      .satisfies(aliasMetadata -> assertThat(aliasMetadata.writeIndex()).isTrue());

    schemaUpdateClientLogs.assertContains("Found pending reindex task with id");
    schemaUpdateClientLogs.assertContains(
      "from index [optimize-users_v1-000001] to [optimize-users_v2-000001], will wait for it to finish."
    );
  }

  @Test
  public void templatedRolledOverIndexDetectRunningReindexAndWaitForIt() throws IOException {
    // given a prepared index with some data in it and being rolled over
    createIndex(TEST_INDEX_WITH_TEMPLATE_V1);
    insertTestDocuments(5);
    ElasticsearchWriterUtil.triggerRollover(prefixAwareClient, TEST_INDEX_WITH_TEMPLATE_V1.getIndexName(), 0);
    insertTestDocuments(5);
    // and the update was run
    final UpdateIndexStep upgradeStep = new UpdateIndexStep(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2);
    final UpgradePlan upgradePlan = createUpdatePlan(upgradeStep);
    // with a throttled reindex (so it is still pending later)
    final HttpRequest reindexRequest = forwardThrottledReindexRequestWithOneDocPerSecond(
      getVersionedIndexName(TEST_INDEX_WITH_TEMPLATE_V1.getIndexName(), 1) + INDEX_SUFFIX_PRE_ROLLOVER,
      getVersionedIndexName(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2.getIndexName(), 2) + INDEX_SUFFIX_PRE_ROLLOVER
    );
    // and getting the reindex status immediately failed and aborted the upgrade
    performUpgradeAndLetReindexStatusCheckFail(upgradePlan, reindexRequest);

    // when it is retried and any new reindex operation would get rejected
    esMockServer
      .when(reindexRequest, Times.unlimited())
      .error(HttpError.error().withDropConnection(true));
    final OffsetDateTime frozenDate = DateCreationFreezer.dateFreezer().freezeDateAndReturn();
    upgradeProcedure.performUpgrade(upgradePlan);

    // then it succeeds
    assertUpdateLogIsComplete(upgradeStep, frozenDate);

    final GetIndexResponse newIndex = getIndicesForMapping(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2);
    assertThat(newIndex.getAliases().get(
      getVersionedIndexName(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2.getIndexName(), 2) + INDEX_SUFFIX_PRE_ROLLOVER
    ))
      .singleElement()
      .satisfies(aliasMetadata -> assertThat(aliasMetadata.writeIndex()).isFalse());
    assertThat(newIndex.getAliases().get(
      getVersionedIndexName(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2.getIndexName(), 2) + NEWEST_INDEX_SUFFIX
    ))
      .singleElement()
      .satisfies(aliasMetadata -> assertThat(aliasMetadata.writeIndex()).isTrue());

    schemaUpdateClientLogs.assertContains("Found pending reindex task with id");
    schemaUpdateClientLogs.assertContains(
      "from index [optimize-users_v1-000001] to [optimize-users_v2-000001], will wait for it to finish."
    );
  }

  @Test
  public void singleIndexDetectFinishedReindexAndSkipIt() throws IOException {
    // given a prepared index with some data in it
    createIndex(TEST_INDEX_V1);
    insertTestDocuments(5);
    // and the update was run
    final UpdateIndexStep upgradeStep = new UpdateIndexStep(TEST_INDEX_V2);
    final UpgradePlan upgradePlan = createUpdatePlan(upgradeStep);
    // and getting the reindex status immediately failed and aborted the upgrade
    performUpgradeAndLetReindexStatusCheckFail(upgradePlan, createReindexRequestMatcher());

    // when it is retried and any new reindex operation would get rejected
    esMockServer
      .when(createReindexRequestMatcher(), Times.unlimited())
      .error(HttpError.error().withDropConnection(true));
    final OffsetDateTime frozenDate = DateCreationFreezer.dateFreezer().freezeDateAndReturn();
    upgradeProcedure.performUpgrade(upgradePlan);

    // then it succeeds
    assertUpdateLogIsComplete(upgradeStep, frozenDate);

    final GetIndexResponse newIndex = getIndicesForMapping(TEST_INDEX_V2);
    assertThat(newIndex.getAliases()).hasSize(1);
    assertThat(newIndex.getAliases().get(getVersionedIndexName(TEST_INDEX_V2.getIndexName(), 2)))
      .singleElement()
      .satisfies(aliasMetadata -> assertThat(aliasMetadata.writeIndex()).isTrue());

    schemaUpdateClientLogs.assertContains(
      "Found that index [optimize-users_v2] already contains " +
        "the same amount of documents as [optimize-users_v1], will skip reindex."
    );
  }

  @Test
  public void templatedIndexDetectFinishedReindexAndSkipIt() throws IOException {
    // given a prepared index with some data in it
    createIndex(TEST_INDEX_WITH_TEMPLATE_V1);
    insertTestDocuments(5);
    // and the update was run
    final UpdateIndexStep upgradeStep = new UpdateIndexStep(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2);
    final UpgradePlan upgradePlan = createUpdatePlan(upgradeStep);
    // and getting the reindex status immediately failed and aborted the upgrade
    performUpgradeAndLetReindexStatusCheckFail(upgradePlan, createReindexRequestMatcher());

    // when it is retried and any new reindex operation would get rejected
    esMockServer
      .when(createReindexRequestMatcher(), Times.unlimited())
      .error(HttpError.error().withDropConnection(true));
    final OffsetDateTime frozenDate = DateCreationFreezer.dateFreezer().freezeDateAndReturn();
    upgradeProcedure.performUpgrade(upgradePlan);

    // then it succeeds
    assertUpdateLogIsComplete(upgradeStep, frozenDate);

    final GetIndexResponse newIndex = getIndicesForMapping(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2);
    assertThat(newIndex.getAliases().get(
      getVersionedIndexName(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2.getIndexName(), 2) + INDEX_SUFFIX_PRE_ROLLOVER
    ))
      .singleElement()
      .satisfies(aliasMetadata -> assertThat(aliasMetadata.writeIndex()).isTrue());

    schemaUpdateClientLogs.assertContains(
      "Found that index [optimize-users_v2-000001] already contains " +
        "the same amount of documents as [optimize-users_v1-000001], will skip reindex."
    );
  }

  @Test
  public void templatedRolledOverIndexDetectFinishedReindexAndSkipIt() throws IOException {
    // given a prepared index with some data in it and being rolled over
    createIndex(TEST_INDEX_WITH_TEMPLATE_V1);
    insertTestDocuments(5);
    ElasticsearchWriterUtil.triggerRollover(prefixAwareClient, TEST_INDEX_WITH_TEMPLATE_V1.getIndexName(), 0);
    insertTestDocuments(5);
    // and the update was run
    final UpdateIndexStep upgradeStep = new UpdateIndexStep(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2);
    final UpgradePlan upgradePlan = createUpdatePlan(upgradeStep);
    // and getting the reindex status immediately failed and aborted the upgrade
    final HttpRequest reindexRequest = createReindexRequestMatcher(
      getVersionedIndexName(TEST_INDEX_WITH_TEMPLATE_V1.getIndexName(), 1) + INDEX_SUFFIX_PRE_ROLLOVER,
      getVersionedIndexName(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2.getIndexName(), 2) + INDEX_SUFFIX_PRE_ROLLOVER
    );
    performUpgradeAndLetReindexStatusCheckFail(upgradePlan, reindexRequest);

    // when it is retried and any new reindex operation would get rejected
    esMockServer
      .when(reindexRequest, Times.unlimited())
      .error(HttpError.error().withDropConnection(true));
    final OffsetDateTime frozenDate = DateCreationFreezer.dateFreezer().freezeDateAndReturn();
    upgradeProcedure.performUpgrade(upgradePlan);

    // then it succeeds
    assertUpdateLogIsComplete(upgradeStep, frozenDate);

    final GetIndexResponse newIndex = getIndicesForMapping(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2);
    assertThat(newIndex.getAliases().get(
      getVersionedIndexName(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2.getIndexName(), 2) + INDEX_SUFFIX_PRE_ROLLOVER
    ))
      .singleElement()
      .satisfies(aliasMetadata -> assertThat(aliasMetadata.writeIndex()).isFalse());
    assertThat(newIndex.getAliases().get(
      getVersionedIndexName(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2.getIndexName(), 2) + NEWEST_INDEX_SUFFIX
    ))
      .singleElement()
      .satisfies(aliasMetadata -> assertThat(aliasMetadata.writeIndex()).isTrue());

    schemaUpdateClientLogs.assertContains(
      "Found that index [optimize-users_v2-000001] already contains " +
        "the same amount of documents as [optimize-users_v1-000001], will skip reindex."
    );
  }

  private void performUpgradeAndLetReindexStatusCheckFail(final UpgradePlan upgradePlan,
                                                          final HttpRequest reindexRequest) {
    final HttpRequest getReindexStatusRequest = createTaskStatusRequestTestMatcher();
    esMockServer
      .when(getReindexStatusRequest, Times.exactly(1))
      .error(HttpError.error().withDropConnection(true));
    assertThatThrownBy(() -> upgradeProcedure.performUpgrade(upgradePlan)).isInstanceOf(UpgradeRuntimeException.class);
    esMockServer.verify(reindexRequest, atLeast(1));
    esMockServer.verify(getReindexStatusRequest, exactly(1));
  }

  private void assertUpdateLogIsComplete(final UpdateIndexStep upgradeStep, final OffsetDateTime frozenDate) {
    final List<UpgradeStepLogEntryDto> updateLogEntries = getAllDocumentsOfIndexAs(
      UpdateLogEntryIndex.INDEX_NAME, UpgradeStepLogEntryDto.class
    );
    assertThat(updateLogEntries)
      .contains(
        UpgradeStepLogEntryDto.builder()
          .indexName(getIndexNameWithVersion(upgradeStep.getIndex()))
          .optimizeVersion(TO_VERSION)
          .stepNumber(1)
          .stepType(upgradeStep.getType())
          .appliedDate(frozenDate.toInstant())
          .build()
      );
  }

  private UpgradePlan createUpdatePlan(final UpdateIndexStep upgradeStep) {
    return UpgradePlanBuilder.createUpgradePlan()
      .fromVersion(INTERMEDIATE_VERSION)
      .toVersion(TO_VERSION)
      .addUpgradeStep(upgradeStep)
      .build();
  }

  private void createIndex(final IndexMappingCreator index) {
    upgradeProcedure.performUpgrade(
      UpgradePlanBuilder.createUpgradePlan()
        .fromVersion(FROM_VERSION)
        .toVersion(INTERMEDIATE_VERSION)
        .addUpgradeSteps(ImmutableList.of(new CreateIndexStep(index)))
        .build()
    );
  }

  private HttpRequest forwardThrottledReindexRequestWithOneDocPerSecond(final String sourceIndexName,
                                                                        final String targetIndexName) {
    final HttpRequest reindexRequest = createReindexRequestMatcher(sourceIndexName, targetIndexName);
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

  private HttpRequest createReindexRequestMatcher(final String sourceIndexName, final String targetIndexName) {
    return createReindexRequestMatcher()
      .withBody(JsonBody.json(
        "{" +
          "\"source\": {\"index\": [\"" + sourceIndexName + "\"]}," +
          "\"dest\": {\"index\": \"" + targetIndexName + "\"}" +
          "}",
        MatchType.ONLY_MATCHING_FIELDS
      ));
  }

  private HttpRequest createReindexRequestMatcher() {
    return request().withPath("/_reindex").withMethod(POST);
  }

  private HttpRequest createTaskStatusRequestTestMatcher() {
    return request().withPath("/_tasks/.*").withMethod(GET);
  }

}
