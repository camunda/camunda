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
import org.camunda.optimize.upgrade.es.index.UpdateLogEntryIndex;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import org.camunda.optimize.upgrade.main.UpgradeProcedure;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.service.UpgradeStepLogEntryDto;
import org.camunda.optimize.upgrade.steps.UpgradeStep;
import org.camunda.optimize.upgrade.steps.UpgradeStepType;
import org.camunda.optimize.upgrade.steps.schema.CreateIndexStep;
import org.camunda.optimize.upgrade.steps.schema.DeleteIndexIfExistsStep;
import org.camunda.optimize.upgrade.steps.schema.UpdateIndexStep;
import org.camunda.optimize.upgrade.steps.schema.UpdateMappingIndexStep;
import org.elasticsearch.client.indices.GetIndexResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpError;
import org.mockserver.model.HttpRequest;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static javax.ws.rs.HttpMethod.PUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.verify.VerificationTimes.exactly;

public class UpgradeStepIdempotenceIT extends AbstractUpgradeIT {

  @RegisterExtension
  protected final LogCapturer upgradeProcedureLogs = LogCapturer.create().captureForType(UpgradeProcedure.class);

  @ParameterizedTest(name = "UpgradeStep of type {0} applied on single index can be retried.")
  @MethodSource("getSingleIndexUpdateStepScenarios")
  public void singleIndexUpdateStepIsIdempotentAndCanBeRetried(final UpgradeStepType stepType,
                                                               final List<UpgradeStep> prepareSteps,
                                                               final UpgradeStep upgradeStep) {
    updateStepIsIdempotentAndCanBeRetried(stepType, prepareSteps, upgradeStep);
  }

  private static Stream<Arguments> getSingleIndexUpdateStepScenarios() {
    return generateAllUpgradeStepScenarios(TEST_INDEX_V1, TEST_INDEX_V2);
  }

  @ParameterizedTest(name = "UpgradeStep of type {0} applied on templated index can be retried.")
  @MethodSource("getTemplatedIndexUpdateStepScenarios")
  public void templatedIndexUpdateStepIsIdempotentAndCanBeRetried(final UpgradeStepType stepType,
                                                                  final List<UpgradeStep> prepareSteps,
                                                                  final UpgradeStep upgradeStep) {
    updateStepIsIdempotentAndCanBeRetried(stepType, prepareSteps, upgradeStep);
  }

  private static Stream<Arguments> getTemplatedIndexUpdateStepScenarios() {
    return generateAllUpgradeStepScenarios(TEST_INDEX_WITH_TEMPLATE_V1, TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2);
  }

  @Test
  public void rolledOverTemplatedIndexUpdateStepIsIdempotentAndCanBeRetried() {
    final String indexName = TEST_INDEX_WITH_TEMPLATE_V1.getIndexName();
    updateStepIsIdempotentAndCanBeRetried(
      UpgradeStepType.SCHEMA_UPDATE_INDEX,
      () -> {
        // given a rolled over index scenario
        upgradeProcedure.performUpgrade(
          UpgradePlanBuilder.createUpgradePlan()
            .fromVersion(FROM_VERSION)
            .toVersion(INTERMEDIATE_VERSION)
            .addUpgradeSteps(ImmutableList.of(
              new CreateIndexStep(TEST_INDEX_WITH_TEMPLATE_V1),
              buildInsertTestIndexDataStep(TEST_INDEX_WITH_TEMPLATE_V1)
            ))
            .build()
        );

        ElasticsearchWriterUtil.triggerRollover(prefixAwareClient, indexName, 0);
      },
      new UpdateIndexStep(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2)
    );

    // then it succeeds on resume
    final GetIndexResponse response = getIndicesForMapping(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2);
    assertThat(response.getIndices())
      .hasSize(2)
      .allSatisfy(index -> assertThat(index).contains(getVersionedIndexName(indexName, 2)));
  }

  @Test
  public void rolledOverTemplatedIndexUpdateStepThatIsInterruptedIsIdempotentAndCanBeRetried() {
    final String indexName = TEST_INDEX_WITH_TEMPLATE_V1.getIndexName();
    updateStepIsIdempotentAndCanBeRetried(
      UpgradeStepType.SCHEMA_UPDATE_INDEX,
      () -> {
        // given a rolled over index scenario
        upgradeProcedure.performUpgrade(
          UpgradePlanBuilder.createUpgradePlan()
            .fromVersion(FROM_VERSION)
            .toVersion(INTERMEDIATE_VERSION)
            .addUpgradeSteps(ImmutableList.of(
              new CreateIndexStep(TEST_INDEX_WITH_TEMPLATE_V1),
              buildInsertTestIndexDataStep(TEST_INDEX_WITH_TEMPLATE_V1)
            ))
            .build()
        );

        ElasticsearchWriterUtil.triggerRollover(prefixAwareClient, indexName, 0);
      },
      () -> {
        // when creating the second rolled over index fails the upgrade
        final String targetIndexName = getVersionedIndexName(indexName, 2);
        final HttpRequest createRolledOverIndex2Request = request()
          .withPath("/" + targetIndexName + "-000002")
          .withMethod(PUT);
        esMockServer
          .when(createRolledOverIndex2Request, Times.exactly(1))
          .error(HttpError.error().withDropConnection(true));
        return createRolledOverIndex2Request;
      },
      new UpdateIndexStep(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2)
    );

    // then it succeeds on resume
    final GetIndexResponse response = getIndicesForMapping(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2);
    assertThat(response.getIndices())
      .hasSize(2)
      .allSatisfy(index -> assertThat(index).contains(getVersionedIndexName(indexName, 2)));
  }

  private void updateStepIsIdempotentAndCanBeRetried(final UpgradeStepType stepType,
                                                     final List<UpgradeStep> prepareSteps,
                                                     final UpgradeStep upgradeStep) {
    updateStepIsIdempotentAndCanBeRetried(
      stepType,
      () -> upgradeProcedure.performUpgrade(
        UpgradePlanBuilder.createUpgradePlan()
          .fromVersion(FROM_VERSION)
          .toVersion(INTERMEDIATE_VERSION)
          .addUpgradeSteps(prepareSteps)
          .build()
      ),
      upgradeStep
    );
  }

  private void updateStepIsIdempotentAndCanBeRetried(final UpgradeStepType stepType,
                                                     final Runnable prepareFunction,
                                                     final UpgradeStep upgradeStep) {
    updateStepIsIdempotentAndCanBeRetried(
      stepType,
      prepareFunction,
      () -> {
        final HttpRequest stepOneLogUpsertRequest = createUpdateLogUpsertRequest(upgradeStep);
        esMockServer
          .when(stepOneLogUpsertRequest, Times.exactly(1))
          .error(HttpError.error().withDropConnection(true));
        return stepOneLogUpsertRequest;
      },
      upgradeStep
    );

  }

  private void updateStepIsIdempotentAndCanBeRetried(final UpgradeStepType stepType,
                                                     final Runnable prepareFunction,
                                                     final Supplier<HttpRequest> mockServerFailPreparation,
                                                     final UpgradeStep upgradeStep) {
    // given
    prepareFunction.run();

    final OffsetDateTime frozenDate = DateCreationFreezer.dateFreezer().freezeDateAndReturn();
    final UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .fromVersion(INTERMEDIATE_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(upgradeStep)
        .build();

    final HttpRequest mockRequest = mockServerFailPreparation.get();
    // the upgrade is executed and failed due to the error on writing the step log
    assertThatThrownBy(() -> upgradeProcedure.performUpgrade(upgradePlan)).isInstanceOf(UpgradeRuntimeException.class);
    esMockServer.verify(mockRequest, exactly(1));

    // when it is retried
    final OffsetDateTime frozenDate2 = DateCreationFreezer.dateFreezer()
      .dateToFreeze(frozenDate.plus(10, ChronoUnit.SECONDS))
      .freezeDateAndReturn();
    upgradeProcedure.performUpgrade(upgradePlan);

    // then it succeeds and the whole log is persisted
    upgradeProcedureLogs.assertDoesNotContain("Skipping Step 1/1");
    upgradeProcedureLogs.assertContains("Starting step 1/1");
    upgradeProcedureLogs.assertContains("Successfully finished step 1/1");
    final List<UpgradeStepLogEntryDto> updateLogEntries = getAllDocumentsOfIndexAs(
      UpdateLogEntryIndex.INDEX_NAME, UpgradeStepLogEntryDto.class
    );
    assertThat(updateLogEntries)
      .contains(
        UpgradeStepLogEntryDto.builder()
          .indexName(getIndexNameWithVersion(upgradeStep.getIndex()))
          .optimizeVersion(TO_VERSION)
          .stepNumber(1)
          .stepType(stepType)
          .appliedDate(frozenDate2.toInstant())
          .build()
      );
  }

  private static Stream<Arguments> generateAllUpgradeStepScenarios(final IndexMappingCreator indexVersion1,
                                                                   final IndexMappingCreator indexVersion2) {
    return Stream.of(
      Arguments.of(
        UpgradeStepType.SCHEMA_CREATE_INDEX,
        Collections.emptyList(),
        new CreateIndexStep(indexVersion1)
      ),
      Arguments.of(
        UpgradeStepType.SCHEMA_DELETE_INDEX,
        ImmutableList.of(new CreateIndexStep(indexVersion1)),
        new DeleteIndexIfExistsStep(indexVersion1)
      ),
      Arguments.of(
        UpgradeStepType.SCHEMA_UPDATE_MAPPING,
        ImmutableList.of(new CreateIndexStep(indexVersion1)),
        new UpdateMappingIndexStep(indexVersion1)
      ),
      Arguments.of(
        UpgradeStepType.SCHEMA_UPDATE_INDEX,
        ImmutableList.of(new CreateIndexStep(indexVersion1)),
        new UpdateIndexStep(indexVersion2)
      ),
      Arguments.of(
        UpgradeStepType.DATA_INSERT,
        ImmutableList.of(new CreateIndexStep(indexVersion1)),
        buildInsertTestIndexDataStep(indexVersion1)
      ),
      Arguments.of(
        UpgradeStepType.DATA_DELETE,
        ImmutableList.of(
          new CreateIndexStep(indexVersion1),
          buildInsertTestIndexDataStep(indexVersion1)
        ),
        buildDeleteTestIndexDataStep(indexVersion1)
      ),
      Arguments.of(
        UpgradeStepType.DATA_UPDATE,
        ImmutableList.of(
          new CreateIndexStep(indexVersion1),
          buildInsertTestIndexDataStep(indexVersion1)
        ),
        buildUpdateTestIndexDataStep(indexVersion1)
      )
    );
  }

}
