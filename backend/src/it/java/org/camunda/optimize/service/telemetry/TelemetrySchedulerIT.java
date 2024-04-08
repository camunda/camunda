/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;

import org.camunda.optimize.AbstractPlatformIT;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag(OPENSEARCH_PASSING)
public class TelemetrySchedulerIT extends AbstractPlatformIT {

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
