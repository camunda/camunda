/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.security;

import io.camunda.optimize.dto.optimize.query.TerminatedUserSessionDto;
import io.camunda.optimize.service.AbstractScheduledService;
import io.camunda.optimize.service.db.reader.TerminatedUserSessionReader;
import io.camunda.optimize.service.db.writer.TerminatedUserSessionWriter;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.stereotype.Component;

@Component
public class TerminatedSessionService extends AbstractScheduledService {

  private static final int CLEANUP_INTERVAL_HOURS = 8;
  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(TerminatedSessionService.class);

  private final TerminatedUserSessionReader terminatedUserSessionReader;
  private final TerminatedUserSessionWriter terminatedUserSessionWriter;
  private final ConfigurationService configurationService;

  public TerminatedSessionService(
      final TerminatedUserSessionReader terminatedUserSessionReader,
      final TerminatedUserSessionWriter terminatedUserSessionWriter,
      final ConfigurationService configurationService) {
    this.terminatedUserSessionReader = terminatedUserSessionReader;
    this.terminatedUserSessionWriter = terminatedUserSessionWriter;
    this.configurationService = configurationService;
  }

  @PostConstruct
  public void initScheduledCleanup() {
    startScheduling();
  }

  @PreDestroy
  public void stopScheduledCleanup() {
    stopScheduling();
  }

  @Override
  protected void run() {
    cleanup();
  }

  @Override
  protected Trigger createScheduleTrigger() {
    return new PeriodicTrigger(Duration.ofHours(CLEANUP_INTERVAL_HOURS));
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
            .minus(
                configurationService.getAuthConfiguration().getTokenLifeTimeMinutes(),
                ChronoUnit.MINUTES));
  }
}
