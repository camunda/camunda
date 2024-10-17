/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.main;

import static io.camunda.optimize.service.metadata.Version.getMajorAndMinor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.vdurmont.semver4j.Semver;
import io.camunda.optimize.service.metadata.PreviousVersion;
import io.camunda.optimize.service.metadata.Version;
import io.camunda.optimize.upgrade.AbstractUpgradeIT;
import io.camunda.optimize.upgrade.UpgradeStepsIT;
import io.camunda.optimize.upgrade.es.TaskResponse;
import io.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import io.camunda.optimize.upgrade.plan.UpgradePlan;
import io.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import io.camunda.optimize.upgrade.plan.factories.CurrentVersionNoOperationUpgradePlanFactory;
import io.camunda.optimize.upgrade.steps.schema.CreateIndexStep;
import io.camunda.optimize.upgrade.steps.schema.UpdateIndexStep;
import io.github.netmikey.logunit.api.LogCapturer;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class UpgradeProcedureIT extends AbstractUpgradeIT {

  @RegisterExtension
  protected final LogCapturer logCapturer =
      LogCapturer.create().captureForType(UpgradeProcedure.class);

  private final UpgradePlan previousVersionMajorMinorUpgradePlan =
      new CurrentVersionNoOperationUpgradePlanFactory().createUpgradePlan();

  @Test
  public void upgradeBreaksOnUnsupportedExistingSchemaVersion() {
    // given
    final String metadataIndexVersion = "2.0.0";
    setMetadataVersion(metadataIndexVersion);

    // when
    assertThatThrownBy(() -> upgradeProcedure.performUpgrade(previousVersionMajorMinorUpgradePlan))
        // then
        .isInstanceOf(UpgradeRuntimeException.class)
        .hasMessage(
            String.format(
                "Schema version saved in Metadata [%s] must be one of [%s, %s]",
                metadataIndexVersion,
                PreviousVersion.PREVIOUS_VERSION_MAJOR_MINOR,
                Version.VERSION));

    assertThat(getMetadataVersion()).isEqualTo(metadataIndexVersion);
  }

  @Test
  public void upgradeSucceedsOnSchemaVersionOfPreviousVersion() {
    // given
    setMetadataVersion(PreviousVersion.PREVIOUS_VERSION);

    // when
    assertThatNoException()
        .isThrownBy(() -> upgradeProcedure.performUpgrade(previousVersionMajorMinorUpgradePlan));

    // then
    assertThat(getMetadataVersion()).isEqualTo(Version.VERSION);
  }

  @Test
  public void upgradeSucceedsOnSchemaVersionOfPreviousPatchVersion() {
    // given
    setMetadataVersion(getMajorAndMinor(PreviousVersion.PREVIOUS_VERSION) + ".1");

    // when
    assertThatNoException()
        .isThrownBy(() -> upgradeProcedure.performUpgrade(previousVersionMajorMinorUpgradePlan));

    // then
    assertThat(getMetadataVersion()).isEqualTo(Version.VERSION);
  }

  @Test
  public void upgradeDoesNotFailOnSchemaVersionOfTargetVersion() {
    // given
    setMetadataVersion(Version.VERSION);

    // when
    assertThatNoException()
        .isThrownBy(() -> upgradeProcedure.performUpgrade(previousVersionMajorMinorUpgradePlan));

    // then
    assertThat(getMetadataVersion()).isEqualTo(Version.VERSION);
    logCapturer.assertContains(
        "Target schemaVersion or a newer version is already present, no update to perform to "
            + previousVersionMajorMinorUpgradePlan.getToVersion().getValue()
            + ".");
  }

  @Test
  public void upgradeDoesNotFailOnSchemaVersionNewerAsTargetVersion() {
    // given
    final String metadataVersion = new Semver(Version.VERSION).withIncPatch().getValue();
    setMetadataVersion(metadataVersion);

    // when
    assertThatNoException()
        .isThrownBy(() -> upgradeProcedure.performUpgrade(previousVersionMajorMinorUpgradePlan));

    // then
    assertThat(getMetadataVersion()).isEqualTo(metadataVersion);
    logCapturer.assertContains(
        "Target schemaVersion or a newer version is already present, no update to perform to "
            + previousVersionMajorMinorUpgradePlan.getToVersion().getValue()
            + ".");
  }

  @Test
  public void upgradeDoesNotFailOnOnMissingMetadataIndex() {
    // given
    cleanAllDataFromDatabase();

    // when
    assertThatNoException()
        .isThrownBy(() -> upgradeProcedure.performUpgrade(previousVersionMajorMinorUpgradePlan));

    // then
    logCapturer.assertContains(
        "No Connection to database or no Optimize Metadata index found, skipping update to "
            + previousVersionMajorMinorUpgradePlan.getToVersion().getValue()
            + ".");
  }

  @Test
  public void upgradeExceptionIncludesTaskInformationOnFailure() {
    // given
    setMetadataVersion(PreviousVersion.PREVIOUS_VERSION);

    final UpgradePlan upgradePlan =
        UpgradePlanBuilder.createUpgradePlan()
            .fromVersion(PreviousVersion.PREVIOUS_VERSION)
            .toVersion(Version.VERSION)
            .addUpgradeStep(applyLookupSkip(new CreateIndexStep(TEST_INDEX_V1)))
            .addUpgradeStep(buildInsertTestIndexDataStep(UpgradeStepsIT.TEST_INDEX_V1))
            .addUpgradeStep(
                applyLookupSkip(
                    new UpdateIndexStep(
                        TEST_INDEX_V2,
                        "params.get(ctx._source.someNonExistentField).values();",
                        Collections.emptyMap(),
                        Collections.emptySet())))
            .build();

    // when
    assertThatThrownBy(() -> upgradeProcedure.performUpgrade(upgradePlan))
        // then the logged message includes all of the task error fields
        .isInstanceOf(UpgradeRuntimeException.class)
        .cause()
        .hasMessageContainingAll(
            Arrays.stream(TaskResponse.Error.class.getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(JsonProperty.class))
                .map(field -> field.getAnnotation(JsonProperty.class).value())
                .toArray(CharSequence[]::new));
  }
}
