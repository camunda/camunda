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

public class EventImportSchedulerIT extends AbstractIT {

  @Test
  public void verifyEventImportDisabledByDefault() {
    assertThat(embeddedOptimizeExtension.getConfigurationService().getEventBasedProcessConfiguration().isEnabled(), is(false));
    assertThat(embeddedOptimizeExtension.getIngestedEventImportScheduler().isScheduledToRun(), is(false));
  }

  @Test
  public void testEventImportIsScheduledSuccessfully() {
    embeddedOptimizeExtension.getIngestedEventImportScheduler().startImportScheduling();
    try {
      assertThat(embeddedOptimizeExtension.getIngestedEventImportScheduler().isScheduledToRun(), is(true));
    } finally {
      embeddedOptimizeExtension.getIngestedEventImportScheduler().stopImportScheduling();
    }
  }

  @Test
  public void testEventImportScheduleStoppedSuccessfully() {
    embeddedOptimizeExtension.getIngestedEventImportScheduler().startImportScheduling();
    embeddedOptimizeExtension.getIngestedEventImportScheduler().stopImportScheduling();
    assertThat(embeddedOptimizeExtension.getIngestedEventImportScheduler().isScheduledToRun(), is(false));
  }
  
}
