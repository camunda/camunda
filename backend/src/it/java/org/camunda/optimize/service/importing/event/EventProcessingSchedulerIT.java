/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;

import org.camunda.optimize.AbstractPlatformIT;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag(OPENSEARCH_PASSING)
public class EventProcessingSchedulerIT extends AbstractPlatformIT {

  @Test
  public void verifyEventProcessingSchedulerIsDisabledByDefault() {
    assertThat(embeddedOptimizeExtension.getDefaultEngineConfiguration().isEventImportEnabled())
        .isFalse();
    assertThat(embeddedOptimizeExtension.getEventProcessingScheduler().isScheduledToRun())
        .isFalse();
  }

  @Test
  public void testEventProcessingScheduledSuccessfully() {
    embeddedOptimizeExtension.getEventProcessingScheduler().startScheduling();
    try {
      assertThat(embeddedOptimizeExtension.getEventProcessingScheduler().isScheduledToRun())
          .isTrue();
    } finally {
      embeddedOptimizeExtension.getEventProcessingScheduler().stopScheduling();
    }
  }

  @Test
  public void testEventProcessingScheduleStoppedSuccessfully() {
    embeddedOptimizeExtension.getEventProcessingScheduler().startScheduling();
    embeddedOptimizeExtension.getEventProcessingScheduler().stopScheduling();
    assertThat(embeddedOptimizeExtension.getEventProcessingScheduler().isScheduledToRun())
        .isFalse();
  }
}
