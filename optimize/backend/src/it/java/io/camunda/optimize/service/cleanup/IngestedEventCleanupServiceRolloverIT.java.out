/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package io.camunda.optimize.service.cleanup;
//
// import static org.assertj.core.api.Assertions.assertThat;
//
// import io.camunda.optimize.dto.optimize.query.event.process.EventDto;
// import io.camunda.optimize.dto.optimize.rest.CloudEventRequestDto;
// import io.camunda.optimize.service.util.configuration.cleanup.IngestedEventCleanupConfiguration;
// import java.time.Instant;
// import java.util.List;
// import java.util.stream.Collectors;
// import java.util.stream.Stream;
// import org.junit.jupiter.api.AfterEach;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Test;
//
// public class IngestedEventCleanupServiceRolloverIT extends AbstractCleanupIT {
//
//   @BeforeEach
//   @AfterEach
//   public void beforeAndAfter() {
//     cleanUpEventIndices();
//   }
//
//   @Test
//   public void testIngestedEventCleanup_afterRollover() {
//     // given
//     getIngestedEventCleanupConfiguration().setEnabled(true);
//     final Instant timestampLessThanTtl = getTimestampLessThanIngestedEventsTtl();
//     final List<CloudEventRequestDto> eventsToCleanupIngestedBeforeRollover =
//         ingestionClient.ingestEventBatchWithTimestamp(timestampLessThanTtl, 10);
//     final List<CloudEventRequestDto> eventsToKeepIngestedBeforeRollover =
//         ingestionClient.ingestEventBatchWithTimestamp(Instant.now().minusSeconds(10L), 10);
//
//     embeddedOptimizeExtension
//         .getConfigurationService()
//         .getEventIndexRolloverConfiguration()
//         .setMaxIndexSizeGB(0);
//     embeddedOptimizeExtension.getEventIndexRolloverService().triggerRollover();
//
//     final List<CloudEventRequestDto> eventsToCleanupIngestedAfterRollover =
//         ingestionClient.ingestEventBatchWithTimestamp(timestampLessThanTtl, 10);
//     final List<CloudEventRequestDto> eventsToKeepIngestedAfterRollover =
//         ingestionClient.ingestEventBatchWithTimestamp(Instant.now().minusSeconds(10L), 10);
//     databaseIntegrationTestExtension.refreshAllOptimizeIndices();
//
//     // when
//     embeddedOptimizeExtension.getCleanupScheduler().runCleanup();
//     databaseIntegrationTestExtension.refreshAllOptimizeIndices();
//
//     // then
//     assertThat(databaseIntegrationTestExtension.getAllStoredExternalEvents())
//         .extracting(EventDto::getId)
//         .containsExactlyInAnyOrderElementsOf(
//             Stream.concat(
//                     eventsToKeepIngestedBeforeRollover.stream(),
//                     eventsToKeepIngestedAfterRollover.stream())
//                 .map(CloudEventRequestDto::getId)
//                 .collect(Collectors.toSet()));
//   }
//
//   private IngestedEventCleanupConfiguration getIngestedEventCleanupConfiguration() {
//     return embeddedOptimizeExtension
//         .getConfigurationService()
//         .getCleanupServiceConfiguration()
//         .getIngestedEventCleanupConfiguration();
//   }
// }
