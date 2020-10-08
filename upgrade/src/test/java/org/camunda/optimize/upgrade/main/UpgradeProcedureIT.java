/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.main;

import com.google.common.collect.Lists;
import io.github.netmikey.logunit.api.LogCapturer;
import org.camunda.optimize.service.metadata.PreviousVersion;
import org.camunda.optimize.service.metadata.Version;
import org.camunda.optimize.upgrade.AbstractUpgradeIT;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class UpgradeProcedureIT extends AbstractUpgradeIT {

  @RegisterExtension
  protected final LogCapturer logCapturer = LogCapturer.create().captureForType(UpgradeProcedure.class);

  @BeforeEach
  @Override
  public void setUp() throws Exception {
    super.setUp();
    initSchema(Lists.newArrayList(METADATA_INDEX));
  }

  @Test
  public void upgradeBreaksOnUnsupportedExistingSchemaVersion() {
    // given
    final String metadataIndexVersion = "2.0.0";
    setMetadataVersion(metadataIndexVersion);
    final TestUpgradeProcedure testUpgradeProcedure = new TestUpgradeProcedure(
      PreviousVersion.PREVIOUS_VERSION, Version.VERSION, "it/it-config.yaml"
    );

    // when
    assertThatThrownBy(testUpgradeProcedure::performUpgrade)
      // then
      .isInstanceOf(UpgradeRuntimeException.class)
      .hasMessage(String.format(
        "Schema version saved in Metadata [%s] must be one of [%s, %s]",
        metadataIndexVersion,
        PreviousVersion.PREVIOUS_VERSION,
        Version.VERSION
      ));

    assertThat(getMetadataVersion()).isEqualTo(metadataIndexVersion);
  }

  @Test
  public void upgradeSucceedsOnSchemaVersionOfPreviousVersion() {
    // given
    setMetadataVersion(PreviousVersion.PREVIOUS_VERSION);
    final TestUpgradeProcedure testUpgradeProcedure = new TestUpgradeProcedure(
      PreviousVersion.PREVIOUS_VERSION, Version.VERSION, "it/it-config.yaml"
    );

    // when
    assertThatNoException().isThrownBy(testUpgradeProcedure::performUpgrade);

    // then
    assertThat(getMetadataVersion()).isEqualTo(Version.VERSION);
  }

  @Test
  public void upgradeDoesNotFailOnSchemaVersionOfTargetVersion() {
    // given
    setMetadataVersion(Version.VERSION);
    final TestUpgradeProcedure testUpgradeProcedure = new TestUpgradeProcedure(
      PreviousVersion.PREVIOUS_VERSION, Version.VERSION, "it/it-config.yaml"
    );

    // when
    assertThatNoException().isThrownBy(testUpgradeProcedure::performUpgrade);

    // then
    assertThat(getMetadataVersion()).isEqualTo(Version.VERSION);
    logCapturer.assertContains("Target optionalSchemaVersion is already present, no upgrade to perform.");
  }

  @Test
  public void upgradeDoesNotFailOnOnMissingMetadataIndex() {
    // given
    cleanAllDataFromElasticsearch();
    final TestUpgradeProcedure testUpgradeProcedure = new TestUpgradeProcedure(
      PreviousVersion.PREVIOUS_VERSION, Version.VERSION, "it/it-config.yaml"
    );

    // when
    assertThatNoException().isThrownBy(testUpgradeProcedure::performUpgrade);

    // then
    logCapturer.assertContains("No Connection to elasticsearch or no Optimize Metadata index found, skipping upgrade.");
  }

}
