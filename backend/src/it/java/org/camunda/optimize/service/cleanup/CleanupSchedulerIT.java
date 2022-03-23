/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.cleanup;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.service.util.configuration.cleanup.CleanupConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class CleanupSchedulerIT extends AbstractIT {

  @Test
  public void verifyDisabledByDefault() {
    assertThat(getCleanupServiceConfiguration().isEnabled()).isFalse();
    assertThat(getCleanupScheduler().isScheduledToRun()).isFalse();
  }

  @ParameterizedTest
  @MethodSource("atLeastOneEnabledScenarios")
  public void verifyCleanupScheduledWhenAtLeastOneCleanupEnabled(final CleanupTestConfiguration configuration) {
    // given
    assertThat(getCleanupServiceConfiguration().isEnabled()).isFalse();
    assertThat(getCleanupScheduler().isScheduledToRun()).isFalse();

    // when
    getCleanupServiceConfiguration().getProcessDataCleanupConfiguration()
      .setEnabled(configuration.processCleanupEnabled);
    getCleanupServiceConfiguration().getDecisionCleanupConfiguration()
      .setEnabled(configuration.decisionCleanupEnabled);
    getCleanupServiceConfiguration().getIngestedEventCleanupConfiguration()
      .setEnabled(configuration.ingestedEventCleanupEnabled);
    embeddedOptimizeExtension.reloadConfiguration();

    // then
    assertThat(getCleanupServiceConfiguration().isEnabled()).isTrue();
    assertThat(getCleanupScheduler().isScheduledToRun()).isTrue();
  }

  public static Stream<CleanupTestConfiguration> atLeastOneEnabledScenarios() {
    return Stream.of(
      CleanupTestConfiguration.builder()
        .processCleanupEnabled(true).decisionCleanupEnabled(false).ingestedEventCleanupEnabled(false)
        .build(),
      CleanupTestConfiguration.builder()
        .processCleanupEnabled(false).decisionCleanupEnabled(true).ingestedEventCleanupEnabled(false)
        .build(),
      CleanupTestConfiguration.builder()
        .processCleanupEnabled(false).decisionCleanupEnabled(false).ingestedEventCleanupEnabled(true)
        .build(),
      CleanupTestConfiguration.builder()
        .processCleanupEnabled(false).decisionCleanupEnabled(true).ingestedEventCleanupEnabled(true)
        .build(),
      CleanupTestConfiguration.builder()
        .processCleanupEnabled(true).decisionCleanupEnabled(true).ingestedEventCleanupEnabled(true)
        .build()
    );
  }

  @Test
  public void testCleanupIsScheduledSuccessfully() {
    getCleanupScheduler().startCleanupScheduling();
    try {
      assertThat(getCleanupScheduler().isScheduledToRun()).isTrue();
    } finally {
      getCleanupScheduler().stopCleanupScheduling();
    }
  }

  @Test
  public void testCleanupScheduledStoppedSuccessfully() {
    getCleanupScheduler().startCleanupScheduling();
    getCleanupScheduler().stopCleanupScheduling();
    assertThat(getCleanupScheduler().isScheduledToRun()).isFalse();
  }

  private CleanupConfiguration getCleanupServiceConfiguration() {
    return embeddedOptimizeExtension.getConfigurationService().getCleanupServiceConfiguration();
  }

  private CleanupScheduler getCleanupScheduler() {
    return embeddedOptimizeExtension.getCleanupScheduler();
  }

  @AllArgsConstructor
  @Builder
  @Data
  private static class CleanupTestConfiguration {
    private final boolean processCleanupEnabled;
    private final boolean decisionCleanupEnabled;
    private final boolean ingestedEventCleanupEnabled;
  }
}
