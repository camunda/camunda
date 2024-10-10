/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade;

import static io.camunda.optimize.service.db.DatabaseConstants.INDEX_SUFFIX_PRE_ROLLOVER;
import static jakarta.ws.rs.HttpMethod.GET;
import static jakarta.ws.rs.HttpMethod.POST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.verify.VerificationTimes.atLeast;
import static org.mockserver.verify.VerificationTimes.exactly;

import com.google.common.collect.ImmutableList;
import io.camunda.optimize.service.db.DatabaseConstants;
import io.camunda.optimize.service.db.schema.IndexMappingCreator;
import io.camunda.optimize.test.util.DateCreationFreezer;
import io.camunda.optimize.upgrade.es.SchemaUpgradeClientES;
import io.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import io.camunda.optimize.upgrade.os.SchemaUpgradeClientOS;
import io.camunda.optimize.upgrade.plan.UpgradePlan;
import io.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import io.camunda.optimize.upgrade.service.UpgradeStepLogEntryDto;
import io.camunda.optimize.upgrade.steps.schema.CreateIndexStep;
import io.camunda.optimize.upgrade.steps.schema.UpdateIndexStep;
import io.github.netmikey.logunit.api.LogCapturer;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockserver.matchers.MatchType;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpError;
import org.mockserver.model.HttpOverrideForwardedRequest;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.JsonBody;

public class UpdateIndexStepResumesReindexOperationsIT extends AbstractUpgradeIT {

  private static final String NEWEST_INDEX_SUFFIX = "-000002";

  @RegisterExtension
  protected final LogCapturer schemaUpdateClientLogs =
      LogCapturer.create()
          .captureForType(SchemaUpgradeClientOS.class)
          .captureForType(SchemaUpgradeClientES.class);

  @Test
  public void singleIndexDetectRunningReindexAndWaitForIt() throws IOException {
    // given a prepared index with some data in it
    createIndex(TEST_INDEX_V1);
    insertTestDocuments(5);
    // and the update was run
    final UpdateIndexStep upgradeStep = createUpdateIndexStep(TEST_INDEX_V2);
    final UpgradePlan upgradePlan = createUpdatePlan(upgradeStep);
    // with a throttled reindex (so it is still pending later)
    final HttpRequest reindexRequest =
        forwardThrottledReindexRequestWithOneDocPerSecond(
            getVersionedIndexName(TEST_INDEX_V1.getIndexName(), 1),
            getVersionedIndexName(TEST_INDEX_V2.getIndexName(), 2));
    // and getting the reindex status immediately failed and aborted the upgrade
    performUpgradeAndLetReindexStatusCheckFail(upgradePlan, reindexRequest);

    // when it is retried and any new reindex operation would get rejected
    dbMockServer
        .when(reindexRequest, Times.unlimited())
        .error(HttpError.error().withDropConnection(true));
    final OffsetDateTime frozenDate = DateCreationFreezer.dateFreezer().freezeDateAndReturn();
    upgradeProcedure.performUpgrade(upgradePlan);

    // then it succeeds
    assertUpdateLogIsComplete(upgradeStep, frozenDate);

    Map<String, Set<String>> aliases =
        getPrefixAwareClient().getAliasesForIndexPattern(TEST_INDEX_V2.getIndexName());
    assertThat(aliases).hasSize(1);
    assertThat(getPrefixAwareClient().getAliasesForIndexPattern(TEST_INDEX_V2.getIndexName()))
        .hasSize(1);
    final List<String> newIndexes =
        databaseIntegrationTestExtension.getAllIndicesWithWriteAlias(TEST_INDEX_V2.getIndexName());
    assertThat(newIndexes).hasSize(1);

    schemaUpdateClientLogs.assertContains("Found pending task with id");
    schemaUpdateClientLogs.assertContains("will wait for it to finish.");
  }

  @Test
  public void templatedIndexDetectRunningReindexAndWaitForIt() throws IOException {
    // given a prepared index with some data in it
    createIndex(TEST_INDEX_WITH_TEMPLATE_V1);
    insertTestDocuments(5);
    // and the update was run
    final UpdateIndexStep upgradeStep =
        createUpdateIndexStep(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2);
    final UpgradePlan upgradePlan = createUpdatePlan(upgradeStep);
    // with a throttled reindex (so it is still pending later)
    final HttpRequest firstIndexReindexRequest =
        forwardThrottledReindexRequestWithOneDocPerSecond(
            getVersionedIndexName(TEST_INDEX_WITH_TEMPLATE_V1.getIndexName(), 1)
                + INDEX_SUFFIX_PRE_ROLLOVER,
            getVersionedIndexName(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2.getIndexName(), 2)
                + INDEX_SUFFIX_PRE_ROLLOVER);
    // and getting the reindex status immediately failed and aborted the upgrade
    performUpgradeAndLetReindexStatusCheckFail(upgradePlan, firstIndexReindexRequest);

    // when it is retried and any new reindex operation would get rejected
    dbMockServer
        .when(firstIndexReindexRequest, Times.unlimited())
        .error(HttpError.error().withDropConnection(true));
    final OffsetDateTime frozenDate = DateCreationFreezer.dateFreezer().freezeDateAndReturn();
    upgradeProcedure.performUpgrade(upgradePlan);

    // then it succeeds
    assertUpdateLogIsComplete(upgradeStep, frozenDate);

    List<String> indicesWithWriteAlias =
        databaseIntegrationTestExtension.getAllIndicesWithWriteAlias(
            TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2.getIndexName());
    assertThat(indicesWithWriteAlias)
        .containsExactly(
            getVersionedIndexName(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2.getIndexName(), 2)
                + INDEX_SUFFIX_PRE_ROLLOVER);

    schemaUpdateClientLogs.assertContains("Found pending task with id");
    schemaUpdateClientLogs.assertContains("will wait for it to finish.");
  }

  @Test
  public void templatedRolledOverIndexDetectRunningReindexAndWaitForIt() throws IOException {
    // given a prepared index with some data in it and being rolled over
    createIndex(TEST_INDEX_WITH_TEMPLATE_V1);
    insertTestDocuments(5);
    getPrefixAwareClient().triggerRollover(TEST_INDEX_WITH_TEMPLATE_V1.getIndexName(), 0);
    insertTestDocuments(5);
    // and the update was run
    final UpdateIndexStep upgradeStep =
        createUpdateIndexStep(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2);
    final UpgradePlan upgradePlan = createUpdatePlan(upgradeStep);
    // with a throttled reindex (so it is still pending later)
    final HttpRequest reindexRequest =
        forwardThrottledReindexRequestWithOneDocPerSecond(
            getVersionedIndexName(TEST_INDEX_WITH_TEMPLATE_V1.getIndexName(), 1)
                + INDEX_SUFFIX_PRE_ROLLOVER,
            getVersionedIndexName(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2.getIndexName(), 2)
                + INDEX_SUFFIX_PRE_ROLLOVER);
    // and getting the reindex status immediately failed and aborted the upgrade
    performUpgradeAndLetReindexStatusCheckFail(upgradePlan, reindexRequest);

    // when it is retried and any new reindex operation would get rejected
    dbMockServer
        .when(reindexRequest, Times.unlimited())
        .error(HttpError.error().withDropConnection(true));
    final OffsetDateTime frozenDate = DateCreationFreezer.dateFreezer().freezeDateAndReturn();
    upgradeProcedure.performUpgrade(upgradePlan);

    // then it succeeds
    assertUpdateLogIsComplete(upgradeStep, frozenDate);

    List<String> indicesReadOnly =
        databaseIntegrationTestExtension.getAllIndicesWithReadOnlyAlias(
            TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2.getIndexName());
    assertThat(indicesReadOnly)
        .containsExactly(
            getVersionedIndexName(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2.getIndexName(), 2)
                + INDEX_SUFFIX_PRE_ROLLOVER);

    List<String> indicesWithWriteAlias =
        databaseIntegrationTestExtension.getAllIndicesWithWriteAlias(
            TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2.getIndexName());
    assertThat(indicesWithWriteAlias)
        .containsExactly(
            getVersionedIndexName(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2.getIndexName(), 2)
                + NEWEST_INDEX_SUFFIX);

    schemaUpdateClientLogs.assertContains("Found pending task with id");
    schemaUpdateClientLogs.assertContains("will wait for it to finish.");
  }

  @Test
  public void singleIndexDetectFinishedReindexAndSkipIt() throws IOException {
    // given a prepared index with some data in it
    createIndex(TEST_INDEX_V1);
    insertTestDocuments(5);
    // and the update was run
    final UpdateIndexStep upgradeStep = createUpdateIndexStep(TEST_INDEX_V2);
    final UpgradePlan upgradePlan = createUpdatePlan(upgradeStep);
    // and getting the reindex status immediately failed and aborted the upgrade
    performUpgradeAndLetReindexStatusCheckFail(upgradePlan, createReindexRequestMatcher());

    // when it is retried and any new reindex operation would get rejected
    dbMockServer
        .when(createReindexRequestMatcher(), Times.unlimited())
        .error(HttpError.error().withDropConnection(true));
    final OffsetDateTime frozenDate = DateCreationFreezer.dateFreezer().freezeDateAndReturn();
    upgradeProcedure.performUpgrade(upgradePlan);

    // then it succeeds
    assertUpdateLogIsComplete(upgradeStep, frozenDate);

    List<String> indicesWithWriteAlias =
        databaseIntegrationTestExtension.getAllIndicesWithWriteAlias(TEST_INDEX_V2.getIndexName());
    assertThat(indicesWithWriteAlias)
        .containsExactly(getVersionedIndexName(TEST_INDEX_V2.getIndexName(), 2));

    schemaUpdateClientLogs.assertContains(
        "Found that index [optimize-users_v2] already contains "
            + "the same amount of documents as [optimize-users_v1], will skip reindex.");
  }

  @Test
  public void templatedIndexDetectFinishedReindexAndSkipIt() throws IOException {
    // given a prepared index with some data in it
    createIndex(TEST_INDEX_WITH_TEMPLATE_V1);
    insertTestDocuments(5);
    // and the update was run
    final UpdateIndexStep upgradeStep =
        createUpdateIndexStep(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2);
    final UpgradePlan upgradePlan = createUpdatePlan(upgradeStep);
    // and getting the reindex status immediately failed and aborted the upgrade
    performUpgradeAndLetReindexStatusCheckFail(upgradePlan, createReindexRequestMatcher());

    // when it is retried and any new reindex operation would get rejected
    dbMockServer
        .when(createReindexRequestMatcher(), Times.unlimited())
        .error(HttpError.error().withDropConnection(true));
    final OffsetDateTime frozenDate = DateCreationFreezer.dateFreezer().freezeDateAndReturn();
    upgradeProcedure.performUpgrade(upgradePlan);

    // then it succeeds
    assertUpdateLogIsComplete(upgradeStep, frozenDate);

    List<String> indices =
        databaseIntegrationTestExtension.getAllIndicesWithWriteAlias(
            TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2.getIndexName());
    assertThat(indices)
        .containsExactly(
            getVersionedIndexName(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2.getIndexName(), 2)
                + INDEX_SUFFIX_PRE_ROLLOVER);

    schemaUpdateClientLogs.assertContains(
        "Found that index [optimize-users_v2-000001] already contains "
            + "the same amount of documents as [optimize-users_v1-000001], will skip reindex.");
  }

  @Test
  public void templatedRolledOverIndexDetectFinishedReindexAndSkipIt() throws IOException {
    // given a prepared index with some data in it and being rolled over
    createIndex(TEST_INDEX_WITH_TEMPLATE_V1);
    insertTestDocuments(5);
    getPrefixAwareClient().triggerRollover(TEST_INDEX_WITH_TEMPLATE_V1.getIndexName(), 0);
    insertTestDocuments(5);
    // and the update was run
    final UpdateIndexStep upgradeStep =
        createUpdateIndexStep(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2);
    final UpgradePlan upgradePlan = createUpdatePlan(upgradeStep);
    // and getting the reindex status immediately failed and aborted the upgrade
    final HttpRequest reindexRequest =
        createReindexRequestMatcher(
            getVersionedIndexName(TEST_INDEX_WITH_TEMPLATE_V1.getIndexName(), 1)
                + INDEX_SUFFIX_PRE_ROLLOVER,
            getVersionedIndexName(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2.getIndexName(), 2)
                + INDEX_SUFFIX_PRE_ROLLOVER);
    performUpgradeAndLetReindexStatusCheckFail(upgradePlan, reindexRequest);

    // when it is retried and any new reindex operation would get rejected
    dbMockServer
        .when(reindexRequest, Times.unlimited())
        .error(HttpError.error().withDropConnection(true));
    final OffsetDateTime frozenDate = DateCreationFreezer.dateFreezer().freezeDateAndReturn();
    upgradeProcedure.performUpgrade(upgradePlan);

    // then it succeeds
    assertUpdateLogIsComplete(upgradeStep, frozenDate);

    List<String> indicesReadOnly =
        databaseIntegrationTestExtension.getAllIndicesWithReadOnlyAlias(
            TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2.getIndexName());
    assertThat(indicesReadOnly)
        .containsExactly(
            getVersionedIndexName(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2.getIndexName(), 2)
                + INDEX_SUFFIX_PRE_ROLLOVER);

    List<String> indicesWithWriteAlias =
        databaseIntegrationTestExtension.getAllIndicesWithWriteAlias(
            TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2.getIndexName());
    assertThat(indicesWithWriteAlias)
        .containsExactly(
            getVersionedIndexName(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2.getIndexName(), 2)
                + NEWEST_INDEX_SUFFIX);

    schemaUpdateClientLogs.assertContains(
        "Found that index [optimize-users_v2-000001] already contains "
            + "the same amount of documents as [optimize-users_v1-000001], will skip reindex.");
  }

  private void performUpgradeAndLetReindexStatusCheckFail(
      final UpgradePlan upgradePlan, final HttpRequest reindexRequest) {
    final HttpRequest getReindexStatusRequest = createTaskStatusRequestTestMatcher();
    dbMockServer
        .when(getReindexStatusRequest, Times.exactly(1))
        .error(HttpError.error().withDropConnection(true));
    assertThatThrownBy(() -> upgradeProcedure.performUpgrade(upgradePlan))
        .isInstanceOf(UpgradeRuntimeException.class);
    dbMockServer.verify(reindexRequest, atLeast(1));
    dbMockServer.verify(getReindexStatusRequest, exactly(1));
  }

  private void assertUpdateLogIsComplete(
      final UpdateIndexStep upgradeStep, final OffsetDateTime frozenDate) {
    final List<UpgradeStepLogEntryDto> updateLogEntries =
        getAllDocumentsOfIndexAs(
            DatabaseConstants.UPDATE_LOG_ENTRY_INDEX_NAME, UpgradeStepLogEntryDto.class);
    assertThat(updateLogEntries)
        .contains(
            UpgradeStepLogEntryDto.builder()
                .indexName(getIndexNameWithVersion(upgradeStep.getIndex()))
                .optimizeVersion(TO_VERSION)
                .stepNumber(1)
                .stepType(upgradeStep.getType())
                .appliedDate(frozenDate.toInstant())
                .build());
  }

  private static UpdateIndexStep createUpdateIndexStep(final IndexMappingCreator index) {
    return applyLookupSkip(new UpdateIndexStep(index));
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
            .addUpgradeSteps(ImmutableList.of(applyLookupSkip(new CreateIndexStep(index))))
            .build());
  }

  private HttpRequest forwardThrottledReindexRequestWithOneDocPerSecond(
      final String sourceIndexName, final String targetIndexName) {
    final HttpRequest reindexRequest =
        createReindexRequestMatcher(sourceIndexName, targetIndexName);
    dbMockServer
        .when(reindexRequest, Times.exactly(1))
        .forward(
            HttpOverrideForwardedRequest.forwardOverriddenRequest(
                createReindexRequestMatcher()
                    .withBody(
                        JsonBody.json(
                            "{"
                                + "\"source\": {\"index\": \""
                                + sourceIndexName
                                + "\", \"size\": \"1\"},"
                                + "\"dest\": {\"index\": \""
                                + targetIndexName
                                + "\"}"
                                + "}"))
                    .withQueryStringParameter("requests_per_second", "1.0")));
    return reindexRequest;
  }

  private HttpRequest createReindexRequestMatcher(
      final String sourceIndexName, final String targetIndexName) {
    return createReindexRequestMatcher()
        .withBody(
            JsonBody.json(
                "{"
                    + "\"source\": {\"index\": [\""
                    + sourceIndexName
                    + "\"]},"
                    + "\"dest\": {\"index\": \""
                    + targetIndexName
                    + "\"}"
                    + "}",
                MatchType.ONLY_MATCHING_FIELDS));
  }

  private HttpRequest createReindexRequestMatcher() {
    return request().withPath("/_reindex").withMethod(POST);
  }

  private HttpRequest createTaskStatusRequestTestMatcher() {
    return request().withPath("/_tasks/.*").withMethod(GET);
  }
}
