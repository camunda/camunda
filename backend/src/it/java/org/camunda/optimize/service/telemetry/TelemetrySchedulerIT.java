/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.telemetry;

import org.camunda.optimize.AbstractIT;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TelemetrySchedulerIT extends AbstractIT {

  @Test
  public void telemetrySchedulerIsStartedByDefault() {
    assertThat(embeddedOptimizeExtension.getTelemetryScheduler().isScheduledToRun()).isTrue();
  }

  @Test
  public void telemetrySchedulerStoppedSuccessfully() {
    embeddedOptimizeExtension.getTelemetryScheduler().stopTelemetryScheduling();
    try {
      assertThat(embeddedOptimizeExtension.getTelemetryScheduler().isScheduledToRun()).isFalse();
    } finally {
      embeddedOptimizeExtension.getTelemetryScheduler().startTelemetryScheduling();
    }
  }
}
