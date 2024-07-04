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
// import static io.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;
// import static org.assertj.core.api.Assertions.assertThat;
//
// import io.camunda.optimize.AbstractPlatformIT;
// import io.camunda.optimize.dto.optimize.query.event.process.EventDto;
// import io.camunda.optimize.dto.optimize.rest.CloudEventRequestDto;
// import io.camunda.optimize.service.util.configuration.cleanup.IngestedEventCleanupConfiguration;
// import java.time.Instant;
// import java.time.OffsetDateTime;
// import java.util.List;
// import java.util.stream.Collectors;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Tag;
// import org.junit.jupiter.api.Test;
//
// @Tag(OPENSEARCH_PASSING)
// public class IngestedEventCleanupIT extends AbstractPlatformIT {
//
//   @BeforeEach
//   public void enableIngestedEventCleanup() {
//     getIngestedEventCleanupConfiguration().setEnabled(true);
//   }
//
//   @Test
//   public void testCleanup() {
//     // given
//     final Instant timestampLessThanTtl = getTimestampLessThanIngestedEventsTtl();
//     final List<CloudEventRequestDto> eventsToCleanup =
//         ingestionClient.ingestEventBatchWithTimestamp(timestampLessThanTtl, 10);
//     final List<CloudEventRequestDto> eventsToKeep =
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
//             eventsToKeep.stream().map(CloudEventRequestDto::getId).collect(Collectors.toSet()));
//   }
//
//   @Test
//   public void testCleanup_disabled() {
//     // given
//     getIngestedEventCleanupConfiguration().setEnabled(false);
//     final Instant timestampLessThanTtl = getTimestampLessThanIngestedEventsTtl();
//     final List<CloudEventRequestDto> eventsToKeep =
//         ingestionClient.ingestEventBatchWithTimestamp(timestampLessThanTtl, 10);
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
//             eventsToKeep.stream().map(CloudEventRequestDto::getId).collect(Collectors.toSet()));
//   }
//
//   private IngestedEventCleanupConfiguration getIngestedEventCleanupConfiguration() {
//     return embeddedOptimizeExtension
//         .getConfigurationService()
//         .getCleanupServiceConfiguration()
//         .getIngestedEventCleanupConfiguration();
//   }
//
//   private Instant getTimestampLessThanIngestedEventsTtl() {
//     return OffsetDateTime.now()
//         .minus(
//             embeddedOptimizeExtension
//                 .getConfigurationService()
//                 .getCleanupServiceConfiguration()
//                 .getTtl())
//         .minusSeconds(1)
//         .toInstant();
//   }
// }
