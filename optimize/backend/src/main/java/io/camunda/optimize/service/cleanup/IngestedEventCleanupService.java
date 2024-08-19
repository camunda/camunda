/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.cleanup;

import io.camunda.optimize.service.db.writer.ExternalEventWriter;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.cleanup.CleanupConfiguration;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class IngestedEventCleanupService extends CleanupService {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(IngestedEventCleanupService.class);
  private final ConfigurationService configurationService;
  private final ExternalEventWriter externalEventWriter;

  public IngestedEventCleanupService(
      final ConfigurationService configurationService,
      final ExternalEventWriter externalEventWriter) {
    this.configurationService = configurationService;
    this.externalEventWriter = externalEventWriter;
  }

  @Override
  public boolean isEnabled() {
    return getCleanupConfiguration().getIngestedEventCleanupConfiguration().isEnabled();
  }

  @Override
  public void doCleanup(final OffsetDateTime startTime) {
    final OffsetDateTime endDate = startTime.minus(getCleanupConfiguration().getTtl());
    log.info(
        "Performing cleanup on external ingested events with a timestamp older than {}", endDate);
    externalEventWriter.deleteEventsOlderThan(endDate);
    log.info(
        "Finished cleanup on external ingested events with a timestamp older than {}", endDate);
  }

  private CleanupConfiguration getCleanupConfiguration() {
    return configurationService.getCleanupServiceConfiguration();
  }
}
