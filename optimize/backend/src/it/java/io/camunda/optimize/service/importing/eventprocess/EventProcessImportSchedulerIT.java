/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package io.camunda.optimize.service.importing.eventprocess;
//
// import static io.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;
// import static org.assertj.core.api.Assertions.assertThat;
//
// import io.camunda.optimize.AbstractPlatformIT;
// import org.junit.jupiter.api.Tag;
// import org.junit.jupiter.api.Test;
//
// @Tag(OPENSEARCH_PASSING)
// public class EventProcessImportSchedulerIT extends AbstractPlatformIT {
//
//   @Test
//   public void verifyEventImportDisabledByDefault() {
//     assertThat(embeddedOptimizeExtension.getDefaultEngineConfiguration().isEventImportEnabled())
//         .isFalse();
//     assertThat(
//             embeddedOptimizeExtension
//                 .getEventBasedProcessesInstanceImportScheduler()
//                 .isScheduledToRun())
//         .isFalse();
//   }
//
//   @Test
//   public void testEventImportIsScheduledSuccessfully() {
//     embeddedOptimizeExtension
//         .getEventBasedProcessesInstanceImportScheduler()
//         .startImportScheduling();
//     try {
//       assertThat(
//               embeddedOptimizeExtension
//                   .getEventBasedProcessesInstanceImportScheduler()
//                   .isScheduledToRun())
//           .isTrue();
//     } finally {
//       embeddedOptimizeExtension
//           .getEventBasedProcessesInstanceImportScheduler()
//           .stopImportScheduling();
//     }
//   }
//
//   @Test
//   public void testEventImportScheduleStoppedSuccessfully() {
//     embeddedOptimizeExtension
//         .getEventBasedProcessesInstanceImportScheduler()
//         .startImportScheduling();
//     embeddedOptimizeExtension
//         .getEventBasedProcessesInstanceImportScheduler()
//         .stopImportScheduling();
//     assertThat(
//             embeddedOptimizeExtension
//                 .getEventBasedProcessesInstanceImportScheduler()
//                 .isScheduledToRun())
//         .isFalse();
//   }
// }
