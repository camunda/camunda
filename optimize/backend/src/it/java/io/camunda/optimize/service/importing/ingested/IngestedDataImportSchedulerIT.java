/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package io.camunda.optimize.service.importing.ingested;
//
// import static io.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;
// import static org.assertj.core.api.Assertions.assertThat;
//
// import io.camunda.optimize.AbstractPlatformIT;
// import org.junit.jupiter.api.Tag;
// import org.junit.jupiter.api.Test;
//
// @Tag(OPENSEARCH_PASSING)
// public class IngestedDataImportSchedulerIT extends AbstractPlatformIT {
//
//   @Test
//   public void ingestedDataImportSchedulerIsDisabledByDefault() {
//     embeddedOptimizeExtension.startContinuousImportScheduling();
//     assertThat(getIngestedDataImportScheduler().isScheduledToRun()).isFalse();
//   }
//
//   @Test
//   public void ingestedDataImportSchedulerIsActiveIfEnabledByConfig() {
//     embeddedOptimizeExtension
//         .getConfigurationService()
//         .getExternalVariableConfiguration()
//         .getImportConfiguration()
//         .setEnabled(true);
//     embeddedOptimizeExtension.startContinuousImportScheduling();
//     assertThat(getIngestedDataImportScheduler().isScheduledToRun()).isTrue();
//   }
//
//   @Test
//   public void ingestedDataImportSchedulerScheduledSuccessfully() {
//     getIngestedDataImportScheduler().stopImportScheduling();
//     getIngestedDataImportScheduler().startImportScheduling();
//     assertThat(getIngestedDataImportScheduler().isScheduledToRun()).isTrue();
//   }
//
//   @Test
//   public void ingestedDataImportSchedulerStoppedSuccessfully() {
//     getIngestedDataImportScheduler().startImportScheduling();
//     getIngestedDataImportScheduler().stopImportScheduling();
//     assertThat(getIngestedDataImportScheduler().isScheduledToRun()).isFalse();
//   }
//
//   private IngestedDataImportScheduler getIngestedDataImportScheduler() {
//     return embeddedOptimizeExtension
//         .getImportSchedulerManager()
//         .getIngestedDataImportScheduler()
//         .orElseThrow();
//   }
// }
