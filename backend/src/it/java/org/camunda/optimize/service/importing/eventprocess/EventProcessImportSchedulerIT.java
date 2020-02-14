/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.eventprocess;

import org.camunda.optimize.AbstractIT;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class EventProcessImportSchedulerIT extends AbstractIT {

  @Test
  public void verifyEventImportDisabledByDefault() {
    assertThat(embeddedOptimizeExtension.getConfigurationService().getEventBasedProcessConfiguration().isEnabled(), is(false));
    assertThat(embeddedOptimizeExtension.getEventBasedProcessesInstanceImportScheduler().isScheduledToRun(), is(false));
  }

  @Test
  public void testEventImportIsScheduledSuccessfully() {
    embeddedOptimizeExtension.getEventBasedProcessesInstanceImportScheduler().startImportScheduling();
    try {
      assertThat(embeddedOptimizeExtension.getEventBasedProcessesInstanceImportScheduler().isScheduledToRun(), is(true));
    } finally {
      embeddedOptimizeExtension.getEventBasedProcessesInstanceImportScheduler().stopImportScheduling();
    }
  }

  @Test
  public void testEventImportScheduleStoppedSuccessfully() {
    embeddedOptimizeExtension.getEventBasedProcessesInstanceImportScheduler().startImportScheduling();
    embeddedOptimizeExtension.getEventBasedProcessesInstanceImportScheduler().stopImportScheduling();
    assertThat(embeddedOptimizeExtension.getEventBasedProcessesInstanceImportScheduler().isScheduledToRun(), is(false));
  }
  
}
