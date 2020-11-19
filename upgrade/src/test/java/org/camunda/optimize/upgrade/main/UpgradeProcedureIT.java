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
import org.camunda.optimize.upgrade.plan.GenericUpgradeFactory;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class UpgradeProcedureIT extends AbstractUpgradeIT {

  @RegisterExtension
  protected final LogCapturer logCapturer = LogCapturer.create().captureForType(UpgradeProcedure.class);

  private final UpgradePlan upgradePlan = GenericUpgradeFactory.createUpgradePlan();

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

    // when
    assertThatThrownBy(() -> upgradeProcedure.performUpgrade(upgradePlan))
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

    // when
    assertThatNoException().isThrownBy(() -> upgradeProcedure.performUpgrade(upgradePlan));

    // then
    assertThat(getMetadataVersion()).isEqualTo(Version.VERSION);
  }

  @Test
  public void upgradeDoesNotFailOnSchemaVersionOfTargetVersion() {
    // given
    setMetadataVersion(Version.VERSION);

    // when
    assertThatNoException().isThrownBy(() -> upgradeProcedure.performUpgrade(upgradePlan));

    // then
    assertThat(getMetadataVersion()).isEqualTo(Version.VERSION);
    logCapturer.assertContains("Target optionalSchemaVersion is already present, no upgrade to perform.");
  }

  @Test
  public void upgradeDoesNotFailOnOnMissingMetadataIndex() {
    // given
    cleanAllDataFromElasticsearch();

    // when
    assertThatNoException().isThrownBy(() -> upgradeProcedure.performUpgrade(upgradePlan));

    // then
    logCapturer.assertContains("No Connection to elasticsearch or no Optimize Metadata index found, skipping upgrade.");
  }

}
