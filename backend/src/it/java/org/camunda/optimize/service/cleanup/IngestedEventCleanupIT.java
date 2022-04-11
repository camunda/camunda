/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.cleanup;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.query.event.process.EventDto;
import org.camunda.optimize.dto.optimize.rest.CloudEventRequestDto;
import org.camunda.optimize.service.util.configuration.cleanup.IngestedEventCleanupConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class IngestedEventCleanupIT extends AbstractIT {

  @BeforeEach
  private void enableIngestedEventCleanup() {
    getIngestedEventCleanupConfiguration().setEnabled(true);
  }

  @Test
  public void testCleanup() {
    // given
    final Instant timestampLessThanTtl = getTimestampLessThanIngestedEventsTtl();
    final List<CloudEventRequestDto> eventsToCleanup =
      ingestionClient.ingestEventBatchWithTimestamp(timestampLessThanTtl, 10);
    final List<CloudEventRequestDto> eventsToKeep =
      ingestionClient.ingestEventBatchWithTimestamp(Instant.now().minusSeconds(10L), 10);
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    embeddedOptimizeExtension.getCleanupScheduler().runCleanup();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    assertThat(elasticSearchIntegrationTestExtension.getAllStoredExternalEvents())
      .extracting(EventDto::getId)
      .containsExactlyInAnyOrderElementsOf(eventsToKeep.stream().map(CloudEventRequestDto::getId).collect(Collectors.toSet()));
  }

  @Test
  public void testCleanup_disabled() {
    // given
    getIngestedEventCleanupConfiguration().setEnabled(false);
    final Instant timestampLessThanTtl = getTimestampLessThanIngestedEventsTtl();
    final List<CloudEventRequestDto> eventsToKeep =
      ingestionClient.ingestEventBatchWithTimestamp(timestampLessThanTtl, 10);
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    embeddedOptimizeExtension.getCleanupScheduler().runCleanup();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    assertThat(elasticSearchIntegrationTestExtension.getAllStoredExternalEvents())
      .extracting(EventDto::getId)
      .containsExactlyInAnyOrderElementsOf(eventsToKeep.stream().map(CloudEventRequestDto::getId).collect(Collectors.toSet()));
  }

  private IngestedEventCleanupConfiguration getIngestedEventCleanupConfiguration() {
    return embeddedOptimizeExtension.getConfigurationService()
      .getCleanupServiceConfiguration()
      .getIngestedEventCleanupConfiguration();
  }

  private Instant getTimestampLessThanIngestedEventsTtl() {
    return OffsetDateTime.now()
      .minus(embeddedOptimizeExtension.getConfigurationService().getCleanupServiceConfiguration().getTtl())
      .minusSeconds(1)
      .toInstant();
  }
}
