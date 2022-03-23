/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.ingested;

import org.camunda.optimize.AbstractIT;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class IngestedDataImportSchedulerIT extends AbstractIT {

  @Test
  public void ingestedDataImportSchedulerIsDisabledByDefault() {
    embeddedOptimizeExtension.startContinuousImportScheduling();
    assertThat(getIngestedDataImportScheduler().isScheduledToRun()).isFalse();
  }

  @Test
  public void ingestedDataImportSchedulerIsActiveIfEnabledByConfig() {
    embeddedOptimizeExtension.getConfigurationService()
      .getExternalVariableConfiguration()
      .getImportConfiguration()
      .setEnabled(true);
    embeddedOptimizeExtension.startContinuousImportScheduling();
    assertThat(getIngestedDataImportScheduler().isScheduledToRun()).isTrue();
  }

  @Test
  public void ingestedDataImportSchedulerScheduledSuccessfully() {
    getIngestedDataImportScheduler().stopImportScheduling();
    getIngestedDataImportScheduler().startImportScheduling();
    assertThat(getIngestedDataImportScheduler().isScheduledToRun()).isTrue();
  }

  @Test
  public void ingestedDataImportSchedulerStoppedSuccessfully() {
    getIngestedDataImportScheduler().startImportScheduling();
    getIngestedDataImportScheduler().stopImportScheduling();
    assertThat(getIngestedDataImportScheduler().isScheduledToRun()).isFalse();
  }

  private IngestedDataImportScheduler getIngestedDataImportScheduler() {
    return embeddedOptimizeExtension.getImportSchedulerManager().getIngestedDataImportScheduler().orElseThrow();
  }

}
