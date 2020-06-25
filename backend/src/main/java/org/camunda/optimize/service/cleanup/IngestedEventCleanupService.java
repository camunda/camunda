/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.cleanup;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.es.writer.ExternalEventWriter;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.cleanup.IngestedEventCleanupConfiguration;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@AllArgsConstructor
@Component
@Slf4j
public class IngestedEventCleanupService implements CleanupService {
  private final ConfigurationService configurationService;
  private final ExternalEventWriter externalEventWriter;

  @Override
  public boolean isEnabled() {
    return getCleanupConfiguration().isEnabled();
  }

  @Override
  public void doCleanup(final OffsetDateTime startTime) {
    final IngestedEventCleanupConfiguration cleanupConfiguration = getCleanupConfiguration();
    final OffsetDateTime endDate = startTime.minus(cleanupConfiguration.getDefaultTtl());
    log.info("Performing cleanup on external ingested events with a timestamp older than {}", endDate);
    externalEventWriter.deleteEventsOlderThan(endDate);
    log.info("Finished cleanup on external ingested events with a timestamp older than {}", endDate);
  }

  private IngestedEventCleanupConfiguration getCleanupConfiguration() {
    return this.configurationService.getCleanupServiceConfiguration().getIngestedEventCleanupConfiguration();
  }
}
