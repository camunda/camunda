/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.cleanup;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.service.util.configuration.cleanup.OptimizeCleanupConfiguration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CleanupSchedulerIT extends AbstractIT {

  @Test
  public void verifyDisabledByDefault() {
    assertThat(getCleanupServiceConfiguration().isEnabled()).isFalse();
    assertThat(getCleanupScheduler().isScheduledToRun()).isFalse();
  }

  @Test
  public void verifyCleanupScheduledWhenAtLeastOneCleanupEnabled() {
    // given
    assertThat(getCleanupServiceConfiguration().isEnabled()).isFalse();
    assertThat(getCleanupScheduler().isScheduledToRun()).isFalse();

    // when
    getCleanupServiceConfiguration().getEngineDataCleanupConfiguration().setEnabled(true);
    getCleanupServiceConfiguration().getIngestedEventCleanupConfiguration().setEnabled(false);
    embeddedOptimizeExtension.reloadConfiguration();

    // then
    assertThat(getCleanupServiceConfiguration().isEnabled()).isTrue();
    assertThat(getCleanupScheduler().isScheduledToRun()).isTrue();

    // when
    getCleanupServiceConfiguration().getEngineDataCleanupConfiguration().setEnabled(false);
    getCleanupServiceConfiguration().getIngestedEventCleanupConfiguration().setEnabled(true);
    embeddedOptimizeExtension.reloadConfiguration();

    // then
    assertThat(getCleanupServiceConfiguration().isEnabled()).isTrue();
    assertThat(getCleanupScheduler().isScheduledToRun()).isTrue();
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

  private OptimizeCleanupConfiguration getCleanupServiceConfiguration() {
    return embeddedOptimizeExtension.getConfigurationService().getCleanupServiceConfiguration();
  }

  private OptimizeCleanupScheduler getCleanupScheduler() {
    return embeddedOptimizeExtension.getCleanupScheduler();
  }
}
