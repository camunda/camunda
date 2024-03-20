/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.ingested;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;

import org.camunda.optimize.AbstractPlatformIT;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag(OPENSEARCH_PASSING)
public class IngestedDataImportSchedulerIT extends AbstractPlatformIT {

  @Test
  public void ingestedDataImportSchedulerIsDisabledByDefault() {
    embeddedOptimizeExtension.startContinuousImportScheduling();
    assertThat(getIngestedDataImportScheduler().isScheduledToRun()).isFalse();
  }

  @Test
  public void ingestedDataImportSchedulerIsActiveIfEnabledByConfig() {
    embeddedOptimizeExtension
        .getConfigurationService()
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
    return embeddedOptimizeExtension
        .getImportSchedulerManager()
        .getIngestedDataImportScheduler()
        .orElseThrow();
  }
}
