/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.cleanup;

import org.camunda.optimize.dto.optimize.query.event.process.EventDto;
import org.camunda.optimize.dto.optimize.rest.CloudEventRequestDto;
import org.camunda.optimize.service.util.configuration.cleanup.IngestedEventCleanupConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class IngestedEventCleanupServiceRolloverIT extends AbstractCleanupIT {

  @BeforeEach
  @AfterEach
  public void beforeAndAfter() {
    cleanUpEventIndices();
  }

  @Test
  public void testIngestedEventCleanup_afterRollover() {
    // given
    getIngestedEventCleanupConfiguration().setEnabled(true);
    final Instant timestampLessThanTtl = getTimestampLessThanIngestedEventsTtl();
    final List<CloudEventRequestDto> eventsToCleanupIngestedBeforeRollover =
      ingestionClient.ingestEventBatchWithTimestamp(timestampLessThanTtl, 10);
    final List<CloudEventRequestDto> eventsToKeepIngestedBeforeRollover =
      ingestionClient.ingestEventBatchWithTimestamp(Instant.now().minusSeconds(10L), 10);

    embeddedOptimizeExtension.getConfigurationService().getEventIndexRolloverConfiguration().setMaxIndexSizeGB(0);
    embeddedOptimizeExtension.getEventIndexRolloverService().triggerRollover();

    final List<CloudEventRequestDto> eventsToCleanupIngestedAfterRollover =
      ingestionClient.ingestEventBatchWithTimestamp(timestampLessThanTtl, 10);
    final List<CloudEventRequestDto> eventsToKeepIngestedAfterRollover =
      ingestionClient.ingestEventBatchWithTimestamp(Instant.now().minusSeconds(10L), 10);
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    embeddedOptimizeExtension.getCleanupScheduler().runCleanup();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    assertThat(elasticSearchIntegrationTestExtension.getAllStoredExternalEvents())
      .extracting(EventDto::getId)
      .containsExactlyInAnyOrderElementsOf(
        Stream.concat(eventsToKeepIngestedBeforeRollover.stream(), eventsToKeepIngestedAfterRollover.stream())
          .map(CloudEventRequestDto::getId).collect(Collectors.toSet())
      );
  }

  private IngestedEventCleanupConfiguration getIngestedEventCleanupConfiguration() {
    return embeddedOptimizeExtension.getConfigurationService()
      .getCleanupServiceConfiguration()
      .getIngestedEventCleanupConfiguration();
  }

}
