/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.cleanup;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.es.writer.variable.ExternalProcessVariableWriter;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.cleanup.CleanupConfiguration;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@AllArgsConstructor
@Component
@Slf4j
public class ExternalVariableCleanupService implements CleanupService {
  private final ConfigurationService configurationService;
  private final ExternalProcessVariableWriter externalProcessVariableWriter;

  @Override
  public boolean isEnabled() {
    return getCleanupConfiguration().getExternalVariableCleanupConfiguration().isEnabled();
  }

  @Override
  public void doCleanup(final OffsetDateTime startTime) {
    final OffsetDateTime endDate = startTime.minus(getCleanupConfiguration().getTtl());
    log.info("Performing cleanup on external variables with a timestamp older than {}", endDate);
    externalProcessVariableWriter.deleteExternalVariablesIngestedBefore(endDate);
    log.info("Finished cleanup on external variables with a timestamp older than {}", endDate);
  }


  private CleanupConfiguration getCleanupConfiguration() {
    return this.configurationService.getCleanupServiceConfiguration();
  }
}
