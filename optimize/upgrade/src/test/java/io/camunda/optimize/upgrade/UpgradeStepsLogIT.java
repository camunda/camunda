/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade;

import static io.camunda.optimize.upgrade.steps.UpgradeStepType.SCHEMA_CREATE_INDEX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockserver.verify.VerificationTimes.exactly;

import io.camunda.optimize.service.db.DatabaseConstants;
import io.camunda.optimize.service.db.schema.IndexMappingCreator;
import io.camunda.optimize.test.util.DateCreationFreezer;
import io.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import io.camunda.optimize.upgrade.main.UpgradeProcedure;
import io.camunda.optimize.upgrade.plan.UpgradePlan;
import io.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import io.camunda.optimize.upgrade.service.UpgradeStepLogEntryDto;
import io.camunda.optimize.upgrade.steps.UpgradeStepType;
import io.camunda.optimize.upgrade.steps.schema.CreateIndexStep;
import io.camunda.optimize.upgrade.steps.schema.UpdateIndexStep;
import io.github.netmikey.logunit.api.LogCapturer;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpError;
import org.mockserver.model.HttpRequest;

public class UpgradeStepsLogIT extends AbstractUpgradeIT {

  @RegisterExtension
  protected final LogCapturer logs = LogCapturer.create().captureForType(UpgradeProcedure.class);

  @Test
  public void singleUpdateStepIsLogged() {
    // given
    final OffsetDateTime frozenDate = DateCreationFreezer.dateFreezer().freezeDateAndReturn();
    final UpgradePlan upgradePlan =
        UpgradePlanBuilder.createUpgradePlan()
            .fromVersion(FROM_VERSION)
            .toVersion(TO_VERSION)
            .addUpgradeStep(buildCreateIndexStep(TEST_INDEX_V1))
            .build();

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    final Optional<UpgradeStepLogEntryDto> updateLogEntries =
        getDocumentOfIndexByIdAs(
            DatabaseConstants.UPDATE_LOG_ENTRY_INDEX_NAME,
            TO_VERSION
                + "_"
                + SCHEMA_CREATE_INDEX
                + "_"
                + getIndexNameService().getOptimizeIndexNameWithVersion(TEST_INDEX_V1),
            UpgradeStepLogEntryDto.class);
    assertThat(updateLogEntries)
        .isPresent()
        .get()
        .isEqualTo(
            UpgradeStepLogEntryDto.builder()
                .indexName(getIndexNameWithVersion(TEST_INDEX_V1))
                .optimizeVersion(TO_VERSION)
                .stepNumber(1)
                .stepType(SCHEMA_CREATE_INDEX)
                .appliedDate(frozenDate.toInstant())
                .build());
  }

  @Test
  public void multipleUpdateStepAreLogged() {
    // given
    final OffsetDateTime frozenDate = DateCreationFreezer.dateFreezer().freezeDateAndReturn();
    final UpgradePlan upgradePlan =
        UpgradePlanBuilder.createUpgradePlan()
            .fromVersion(FROM_VERSION)
            .toVersion(TO_VERSION)
            .addUpgradeStep(buildCreateIndexStep(TEST_INDEX_V1))
            .addUpgradeStep(buildUpdateIndexStep(TEST_INDEX_V2))
            .build();

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    final List<UpgradeStepLogEntryDto> updateLogEntries =
        getAllDocumentsOfIndexAs(
            DatabaseConstants.UPDATE_LOG_ENTRY_INDEX_NAME, UpgradeStepLogEntryDto.class);
    assertThat(updateLogEntries)
        .containsExactlyInAnyOrder(
            UpgradeStepLogEntryDto.builder()
                .indexName(getIndexNameWithVersion(TEST_INDEX_V1))
                .optimizeVersion(TO_VERSION)
                .stepNumber(1)
                .stepType(SCHEMA_CREATE_INDEX)
                .appliedDate(frozenDate.toInstant())
                .build(),
            UpgradeStepLogEntryDto.builder()
                .indexName(getIndexNameWithVersion(TEST_INDEX_V2))
                .optimizeVersion(TO_VERSION)
                .stepNumber(2)
                .stepType(UpgradeStepType.SCHEMA_UPDATE_INDEX)
                .appliedDate(frozenDate.toInstant())
                .build());
  }

  @Test
  public void onlySuccessfulUpdateStepsAreLogged() {
    // given
    final OffsetDateTime frozenDate = DateCreationFreezer.dateFreezer().freezeDateAndReturn();
    final UpgradePlan upgradePlan =
        UpgradePlanBuilder.createUpgradePlan()
            .fromVersion(FROM_VERSION)
            .toVersion(TO_VERSION)
            .addUpgradeStep(buildCreateIndexStep(TEST_INDEX_V1))
            .addUpgradeStep(buildUpdateIndexStep(TEST_INDEX_V2))
            .build();
    final HttpRequest indexDeleteRequest =
        createIndexDeleteRequest(getIndexNameWithVersion(TEST_INDEX_V1));
    dbMockServer
        .when(indexDeleteRequest, Times.exactly(1))
        .error(HttpError.error().withDropConnection(true));

    assertThatThrownBy(() -> upgradeProcedure.performUpgrade(upgradePlan))
        .isInstanceOf(UpgradeRuntimeException.class);
    dbMockServer.verify(indexDeleteRequest, exactly(1));

    // then only the successful first step is logged
    logs.assertContains(
        "Starting step 1/2: CreateIndexStep on index: " + getIndexNameWithVersion(TEST_INDEX_V1));
    logs.assertContains(
        "Successfully finished step 1/2: CreateIndexStep on index: "
            + getIndexNameWithVersion(TEST_INDEX_V1));
    logs.assertContains("The upgrade will be aborted. Please investigate the cause and retry it..");
    final List<UpgradeStepLogEntryDto> updateLogEntries =
        getAllDocumentsOfIndexAs(
            DatabaseConstants.UPDATE_LOG_ENTRY_INDEX_NAME, UpgradeStepLogEntryDto.class);
    assertThat(updateLogEntries)
        .containsExactlyInAnyOrder(
            UpgradeStepLogEntryDto.builder()
                .indexName(getIndexNameWithVersion(TEST_INDEX_V1))
                .optimizeVersion(TO_VERSION)
                .stepNumber(1)
                .stepType(SCHEMA_CREATE_INDEX)
                .appliedDate(frozenDate.toInstant())
                .build());
  }

  @Test
  public void upgradeIsResumedWhenUpgradeStepRequestFails() {
    // given
    final OffsetDateTime frozenDate = DateCreationFreezer.dateFreezer().freezeDateAndReturn();
    final UpgradePlan upgradePlan =
        UpgradePlanBuilder.createUpgradePlan()
            .fromVersion(FROM_VERSION)
            .toVersion(TO_VERSION)
            .addUpgradeStep(buildCreateIndexStep(TEST_INDEX_V1))
            .addUpgradeStep(buildUpdateIndexStep(TEST_INDEX_V2))
            .build();
    final HttpRequest indexDeleteRequest =
        createIndexDeleteRequest(getIndexNameWithVersion(TEST_INDEX_V1));
    dbMockServer
        .when(indexDeleteRequest, Times.exactly(1))
        .error(HttpError.error().withDropConnection(true));

    // the upgrade is executed and failed
    assertThatThrownBy(() -> upgradeProcedure.performUpgrade(upgradePlan))
        .isInstanceOf(UpgradeRuntimeException.class);
    dbMockServer.verify(indexDeleteRequest, exactly(1));
    getPrefixAwareClient().refresh("*");

    // when it is retried
    final OffsetDateTime frozenDate2 =
        DateCreationFreezer.dateFreezer()
            .dateToFreeze(frozenDate.plus(10, ChronoUnit.SECONDS))
            .freezeDateAndReturn();
    upgradeProcedure.performUpgrade(upgradePlan);

    // then it succeeds and the whole log is persisted
    logs.assertContains(
        String.format(
            "Skipping Step 1/2: CreateIndexStep on index: %s as it was found to be previously completed already at: %s.",
            getIndexNameWithVersion(TEST_INDEX_V1), frozenDate.toInstant()));
    logs.assertContains(
        "Starting step 2/2: UpdateIndexStep on index: " + getIndexNameWithVersion(TEST_INDEX_V2));
    logs.assertContains(
        "Successfully finished step 2/2: UpdateIndexStep on index: "
            + getIndexNameWithVersion(TEST_INDEX_V2));
    final List<UpgradeStepLogEntryDto> updateLogEntries =
        getAllDocumentsOfIndexAs(
            DatabaseConstants.UPDATE_LOG_ENTRY_INDEX_NAME, UpgradeStepLogEntryDto.class);
    assertThat(updateLogEntries)
        .containsExactlyInAnyOrder(
            UpgradeStepLogEntryDto.builder()
                .indexName(getIndexNameWithVersion(TEST_INDEX_V1))
                .optimizeVersion(TO_VERSION)
                .stepNumber(1)
                .stepType(SCHEMA_CREATE_INDEX)
                .appliedDate(frozenDate.toInstant())
                .build(),
            UpgradeStepLogEntryDto.builder()
                .indexName(getIndexNameWithVersion(TEST_INDEX_V2))
                .optimizeVersion(TO_VERSION)
                .stepNumber(2)
                .stepType(UpgradeStepType.SCHEMA_UPDATE_INDEX)
                .appliedDate(frozenDate2.toInstant())
                .build());
  }

  @Test
  public void upgradeIsResumedWhenUpgradeLogUpdateFails() {
    // given
    final OffsetDateTime frozenDate = DateCreationFreezer.dateFreezer().freezeDateAndReturn();
    final CreateIndexStep createIndexStep = buildCreateIndexStep(TEST_INDEX_V1);
    final UpgradePlan upgradePlan =
        UpgradePlanBuilder.createUpgradePlan()
            .fromVersion(FROM_VERSION)
            .toVersion(TO_VERSION)
            .addUpgradeStep(createIndexStep)
            .addUpgradeStep(buildUpdateIndexStep(TEST_INDEX_V2))
            .build();
    final HttpRequest stepOneLogUpsertRequest = createUpdateLogUpsertRequest(createIndexStep);
    dbMockServer
        .when(stepOneLogUpsertRequest, Times.exactly(1))
        .error(HttpError.error().withDropConnection(true));

    // the upgrade is executed and failed
    assertThatThrownBy(() -> upgradeProcedure.performUpgrade(upgradePlan))
        .isInstanceOf(UpgradeRuntimeException.class);
    dbMockServer.verify(stepOneLogUpsertRequest, exactly(1));

    // when it is retried
    final OffsetDateTime frozenDate2 =
        DateCreationFreezer.dateFreezer()
            .dateToFreeze(frozenDate.plus(10, ChronoUnit.SECONDS))
            .freezeDateAndReturn();
    upgradeProcedure.performUpgrade(upgradePlan);

    // then it succeeds and the whole log is persisted
    logs.assertDoesNotContain("Skipping Step 1/2: CreateIndexStep on index");
    logs.assertContains(
        "Starting step 1/2: CreateIndexStep on index: " + getIndexNameWithVersion(TEST_INDEX_V1));
    logs.assertContains(
        "Successfully finished step 1/2: CreateIndexStep on index: "
            + getIndexNameWithVersion(TEST_INDEX_V1));
    logs.assertContains(
        "Starting step 2/2: UpdateIndexStep on index: " + getIndexNameWithVersion(TEST_INDEX_V2));
    logs.assertContains(
        "Successfully finished step 2/2: UpdateIndexStep on index: "
            + getIndexNameWithVersion(TEST_INDEX_V2));
    final List<UpgradeStepLogEntryDto> updateLogEntries =
        getAllDocumentsOfIndexAs(
            DatabaseConstants.UPDATE_LOG_ENTRY_INDEX_NAME, UpgradeStepLogEntryDto.class);
    assertThat(updateLogEntries)
        .containsExactlyInAnyOrder(
            UpgradeStepLogEntryDto.builder()
                .indexName(getIndexNameWithVersion(TEST_INDEX_V1))
                .optimizeVersion(TO_VERSION)
                .stepNumber(1)
                .stepType(SCHEMA_CREATE_INDEX)
                .appliedDate(frozenDate2.toInstant())
                .build(),
            UpgradeStepLogEntryDto.builder()
                .indexName(getIndexNameWithVersion(TEST_INDEX_V2))
                .optimizeVersion(TO_VERSION)
                .stepNumber(2)
                .stepType(UpgradeStepType.SCHEMA_UPDATE_INDEX)
                .appliedDate(frozenDate2.toInstant())
                .build());
  }

  private CreateIndexStep buildCreateIndexStep(final IndexMappingCreator index) {
    return applyLookupSkip(new CreateIndexStep(index));
  }

  private UpdateIndexStep buildUpdateIndexStep(final IndexMappingCreator index) {
    return applyLookupSkip(new UpdateIndexStep(index));
  }
}
