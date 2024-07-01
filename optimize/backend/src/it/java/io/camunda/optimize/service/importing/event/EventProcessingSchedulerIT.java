/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package io.camunda.optimize.service.importing.event;
//
// import static io.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;
// import static org.assertj.core.api.Assertions.assertThat;
//
// import io.camunda.optimize.AbstractPlatformIT;
// import org.junit.jupiter.api.Tag;
// import org.junit.jupiter.api.Test;
//
// @Tag(OPENSEARCH_PASSING)
// public class EventProcessingSchedulerIT extends AbstractPlatformIT {
//
//   @Test
//   public void verifyEventProcessingSchedulerIsDisabledByDefault() {
//     assertThat(embeddedOptimizeExtension.getDefaultEngineConfiguration().isEventImportEnabled())
//         .isFalse();
//     assertThat(embeddedOptimizeExtension.getEventProcessingScheduler().isScheduledToRun())
//         .isFalse();
//   }
//
//   @Test
//   public void testEventProcessingScheduledSuccessfully() {
//     embeddedOptimizeExtension.getEventProcessingScheduler().startScheduling();
//     try {
//       assertThat(embeddedOptimizeExtension.getEventProcessingScheduler().isScheduledToRun())
//           .isTrue();
//     } finally {
//       embeddedOptimizeExtension.getEventProcessingScheduler().stopScheduling();
//     }
//   }
//
//   @Test
//   public void testEventProcessingScheduleStoppedSuccessfully() {
//     embeddedOptimizeExtension.getEventProcessingScheduler().startScheduling();
//     embeddedOptimizeExtension.getEventProcessingScheduler().stopScheduling();
//     assertThat(embeddedOptimizeExtension.getEventProcessingScheduler().isScheduledToRun())
//         .isFalse();
//   }
// }
