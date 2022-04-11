/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.eventprocess;

import org.camunda.optimize.AbstractIT;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EventProcessImportSchedulerIT extends AbstractIT {

  @Test
  public void verifyEventImportDisabledByDefault() {
    assertThat(embeddedOptimizeExtension.getDefaultEngineConfiguration().isEventImportEnabled()).isFalse();
    assertThat(embeddedOptimizeExtension.getEventBasedProcessesInstanceImportScheduler().isScheduledToRun()).isFalse();
  }

  @Test
  public void testEventImportIsScheduledSuccessfully() {
    embeddedOptimizeExtension.getEventBasedProcessesInstanceImportScheduler().startImportScheduling();
    try {
      assertThat(embeddedOptimizeExtension.getEventBasedProcessesInstanceImportScheduler().isScheduledToRun()).isTrue();
    } finally {
      embeddedOptimizeExtension.getEventBasedProcessesInstanceImportScheduler().stopImportScheduling();
    }
  }

  @Test
  public void testEventImportScheduleStoppedSuccessfully() {
    embeddedOptimizeExtension.getEventBasedProcessesInstanceImportScheduler().startImportScheduling();
    embeddedOptimizeExtension.getEventBasedProcessesInstanceImportScheduler().stopImportScheduling();
    assertThat(embeddedOptimizeExtension.getEventBasedProcessesInstanceImportScheduler().isScheduledToRun()).isFalse();
  }

}
