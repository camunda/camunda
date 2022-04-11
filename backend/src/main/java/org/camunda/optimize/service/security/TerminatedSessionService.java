/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.TerminatedUserSessionDto;
import org.camunda.optimize.service.AbstractScheduledService;
import org.camunda.optimize.service.es.reader.TerminatedUserSessionReader;
import org.camunda.optimize.service.es.writer.TerminatedUserSessionWriter;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@Component
@Slf4j
public class TerminatedSessionService extends AbstractScheduledService {
  private static final int CLEANUP_INTERVAL_HOURS = 8;

  private final TerminatedUserSessionReader terminatedUserSessionReader;
  private final TerminatedUserSessionWriter terminatedUserSessionWriter;
  private final ConfigurationService configurationService;

  @PostConstruct
  public void initScheduledCleanup() {
    startScheduling();
  }

  @PreDestroy
  public void stopScheduledCleanup() {
    stopScheduling();
  }

  @Override
  protected Trigger createScheduleTrigger() {
    return new PeriodicTrigger(CLEANUP_INTERVAL_HOURS, TimeUnit.HOURS);
  }

  @Override
  protected void run() {
    cleanup();
  }

  public void terminateUserSession(final String sessionId) {
    final TerminatedUserSessionDto sessionDto = new TerminatedUserSessionDto(sessionId);

    terminatedUserSessionWriter.writeTerminatedUserSession(sessionDto);
  }

  public boolean isSessionTerminated(final String sessionId) {
    return terminatedUserSessionReader.exists(sessionId);
  }

  public void cleanup() {
    log.debug("Cleaning up terminated user sessions.");
    terminatedUserSessionWriter.deleteTerminatedUserSessionsOlderThan(
      LocalDateUtil.getCurrentDateTime()
        .minus(configurationService.getAuthConfiguration().getTokenLifeTimeMinutes(), ChronoUnit.MINUTES)
    );
  }
}
