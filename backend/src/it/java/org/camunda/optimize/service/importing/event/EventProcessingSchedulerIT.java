/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.event;

import org.camunda.optimize.AbstractIT;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class EventProcessingSchedulerIT extends AbstractIT {

  @Test
  public void verifyEventProcessingSchedulerIsDisabledByDefault() {
    assertThat(embeddedOptimizeExtension.getConfigurationService().getEventBasedProcessConfiguration().isEnabled(), is(false));
    assertThat(embeddedOptimizeExtension.getEventProcessingScheduler().isScheduledToRun(), is(false));
  }

  @Test
  public void testEventProcessingScheduledSuccessfully() {
    embeddedOptimizeExtension.getEventProcessingScheduler().startScheduling();
    try {
      assertThat(embeddedOptimizeExtension.getEventProcessingScheduler().isScheduledToRun(), is(true));
    } finally {
      embeddedOptimizeExtension.getEventProcessingScheduler().stopScheduling();
    }
  }

  @Test
  public void testEventProcessingScheduleStoppedSuccessfully() {
    embeddedOptimizeExtension.getEventProcessingScheduler().startScheduling();
    embeddedOptimizeExtension.getEventProcessingScheduler().stopScheduling();
    assertThat(embeddedOptimizeExtension.getEventProcessingScheduler().isScheduledToRun(), is(false));
  }
  
}
