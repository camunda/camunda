/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.event;

import org.camunda.optimize.AbstractIT;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EventProcessingSchedulerIT extends AbstractIT {

  @Test
  public void verifyEventProcessingSchedulerIsDisabledByDefault() {
    assertThat(embeddedOptimizeExtension.getDefaultEngineConfiguration().isEventImportEnabled()).isFalse();
    assertThat(embeddedOptimizeExtension.getEventProcessingScheduler().isScheduledToRun()).isFalse();
  }

  @Test
  public void testEventProcessingScheduledSuccessfully() {
    embeddedOptimizeExtension.getEventProcessingScheduler().startScheduling();
    try {
      assertThat(embeddedOptimizeExtension.getEventProcessingScheduler().isScheduledToRun()).isTrue();
    } finally {
      embeddedOptimizeExtension.getEventProcessingScheduler().stopScheduling();
    }
  }

  @Test
  public void testEventProcessingScheduleStoppedSuccessfully() {
    embeddedOptimizeExtension.getEventProcessingScheduler().startScheduling();
    embeddedOptimizeExtension.getEventProcessingScheduler().stopScheduling();
    assertThat(embeddedOptimizeExtension.getEventProcessingScheduler().isScheduledToRun()).isFalse();
  }
  
}
