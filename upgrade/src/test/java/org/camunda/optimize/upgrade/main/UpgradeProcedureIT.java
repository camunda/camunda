/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.main;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.vdurmont.semver4j.Semver;
import io.github.netmikey.logunit.api.LogCapturer;
import org.camunda.optimize.service.metadata.PreviousVersion;
import org.camunda.optimize.service.metadata.Version;
import org.camunda.optimize.upgrade.AbstractUpgradeIT;
import org.camunda.optimize.upgrade.UpgradeStepsIT;
import org.camunda.optimize.upgrade.es.TaskResponse;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.plan.factories.CurrentVersionNoOperationUpgradePlanFactory;
import org.camunda.optimize.upgrade.steps.schema.CreateIndexStep;
import org.camunda.optimize.upgrade.steps.schema.UpdateIndexStep;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.camunda.optimize.service.metadata.Version.getMajorAndMinor;

public class UpgradeProcedureIT extends AbstractUpgradeIT {

  @RegisterExtension
  protected final LogCapturer logCapturer = LogCapturer.create().captureForType(UpgradeProcedure.class);

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
      .hasMessage(String.format(
        "Schema version saved in Metadata [%s] must be one of [%s, %s]",
        metadataIndexVersion,
        PreviousVersion.PREVIOUS_VERSION_MAJOR_MINOR,
        Version.VERSION
      ));

    assertThat(getMetadataVersion()).isEqualTo(metadataIndexVersion);
  }

  @Test
  public void upgradeSucceedsOnSchemaVersionOfPreviousVersion() {
    // given
    setMetadataVersion(PreviousVersion.PREVIOUS_VERSION);

    // when
    assertThatNoException().isThrownBy(() -> upgradeProcedure.performUpgrade(previousVersionMajorMinorUpgradePlan));

    // then
    assertThat(getMetadataVersion()).isEqualTo(Version.VERSION);
  }

  @Disabled("Disabled until fixed with OPT-6298")
  @Test
  public void upgradeSucceedsOnSchemaVersionOfPreviousPatchVersion() {
    // given
    setMetadataVersion(getMajorAndMinor(PreviousVersion.PREVIOUS_VERSION) + ".1");

    // when
    assertThatNoException().isThrownBy(() -> upgradeProcedure.performUpgrade(previousVersionMajorMinorUpgradePlan));

    // then
    assertThat(getMetadataVersion()).isEqualTo(Version.VERSION);
  }

  @ParameterizedTest
  @MethodSource("supportedPreviewUpdateScenarios")
  public void upgradeSucceedsForSupportedPreviewUpdateScenarios(final String fromVersion, final String toVersion) {
    // given
    setMetadataVersion(fromVersion);

    // when
    final UpgradePlan upgradePlan = UpgradePlanBuilder.createUpgradePlan()
      .fromVersion(fromVersion)
      .toVersion(toVersion)
      .build();
    assertThatNoException().isThrownBy(() -> upgradeProcedure.performUpgrade(upgradePlan));

    // then
    assertThat(getMetadataVersion()).isEqualTo(toVersion);
  }

  public static Stream<Arguments> supportedPreviewUpdateScenarios() {
    return Stream.of(
      Arguments.of("3.8.0", "3.9.0-preview-1"),
      Arguments.of("3.9.0-preview-1", "3.9.0"),
      Arguments.of("3.9.0-preview-1", "3.9.0-preview-2")
    );
  }

  @ParameterizedTest
  @MethodSource("unsupportedPreviewUpdateScenarios")
  public void upgradeIsSkippedForUnsupportedPreviewUpdateScenarios(final String fromVersion, final String toVersion) {
    // given
    setMetadataVersion(fromVersion);

    // when
    final UpgradePlan upgradePlan = UpgradePlanBuilder.createUpgradePlan()
      .fromVersion(fromVersion)
      .toVersion(toVersion)
      .build();
    assertThatNoException().isThrownBy(() -> upgradeProcedure.performUpgrade(upgradePlan));

    // then
    assertThat(getMetadataVersion()).isEqualTo(fromVersion);
    logCapturer.assertContains(
      "Target schemaVersion or a newer version is already present, no update to perform to "
        + upgradePlan.getToVersion().getValue()
        + "."
    );
  }

  public static Stream<Arguments> unsupportedPreviewUpdateScenarios() {
    return Stream.of(
      Arguments.of("3.9.0-preview-1", "3.8.0"),
      Arguments.of("3.9.0", "3.9.0-preview-1"),
      Arguments.of("3.9.0-preview-2", "3.9.0-preview-1")
    );
  }

  @Test
  public void upgradeDoesNotFailOnSchemaVersionOfTargetVersion() {
    // given
    setMetadataVersion(Version.VERSION);

    // when
    assertThatNoException().isThrownBy(() -> upgradeProcedure.performUpgrade(previousVersionMajorMinorUpgradePlan));

    // then
    assertThat(getMetadataVersion()).isEqualTo(Version.VERSION);
    logCapturer.assertContains(
      "Target schemaVersion or a newer version is already present, no update to perform to "
        + previousVersionMajorMinorUpgradePlan.getToVersion().getValue()
        + "."
    );
  }

  @Test
  public void upgradeDoesNotFailOnSchemaVersionNewerAsTargetVersion() {
    // given
    final String metadataVersion = new Semver(Version.VERSION).withIncPatch().getValue();
    setMetadataVersion(metadataVersion);

    // when
    assertThatNoException().isThrownBy(() -> upgradeProcedure.performUpgrade(previousVersionMajorMinorUpgradePlan));

    // then
    assertThat(getMetadataVersion()).isEqualTo(metadataVersion);
    logCapturer.assertContains(
      "Target schemaVersion or a newer version is already present, no update to perform to "
        + previousVersionMajorMinorUpgradePlan.getToVersion().getValue()
        + "."
    );
  }

  @Test
  public void upgradeDoesNotFailOnOnMissingMetadataIndex() {
    // given
    cleanAllDataFromElasticsearch();

    // when
    assertThatNoException().isThrownBy(() -> upgradeProcedure.performUpgrade(previousVersionMajorMinorUpgradePlan));

    // then
    logCapturer.assertContains(
      "No Connection to elasticsearch or no Optimize Metadata index found, skipping update to "
        + previousVersionMajorMinorUpgradePlan.getToVersion().getValue()
        + "."
    );
  }

  @Test
  public void upgradeExceptionIncludesTaskInformationOnFailure() {
    // given
    setMetadataVersion(PreviousVersion.PREVIOUS_VERSION);

    final UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .fromVersion(PreviousVersion.PREVIOUS_VERSION)
        .toVersion(Version.VERSION)
        .addUpgradeStep(new CreateIndexStep(TEST_INDEX_V1))
        .addUpgradeStep(buildInsertTestIndexDataStep(UpgradeStepsIT.TEST_INDEX_V1))
        .addUpgradeStep(new UpdateIndexStep(
          TEST_INDEX_V2,
          "params.get(ctx._source.someNonExistentField).values();",
          Collections.emptyMap(),
          Collections.emptySet()
        ))
        .build();

    // when
    assertThatThrownBy(() -> upgradeProcedure.performUpgrade(upgradePlan))
      // then the logged message includes all of the task error fields
      .isInstanceOf(UpgradeRuntimeException.class)
      .getCause()
      .hasMessageContainingAll(
        Arrays.stream(TaskResponse.Error.class.getDeclaredFields())
          .filter(field -> field.isAnnotationPresent(JsonProperty.class))
          .map(field -> field.getAnnotation(JsonProperty.class).value())
          .toArray(CharSequence[]::new));
  }
}
