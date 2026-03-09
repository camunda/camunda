/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.security;

import io.camunda.auth.domain.model.TerminatedSession;
import io.camunda.auth.domain.spi.TerminatedSessionPort;
import io.camunda.optimize.dto.optimize.query.TerminatedUserSessionDto;
import io.camunda.optimize.service.db.reader.TerminatedUserSessionReader;
import io.camunda.optimize.service.db.writer.TerminatedUserSessionWriter;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;

@Component
public class OptimizeTerminatedSessionAdapter implements TerminatedSessionPort {

  private final TerminatedUserSessionReader reader;
  private final TerminatedUserSessionWriter writer;

  public OptimizeTerminatedSessionAdapter(
      final TerminatedUserSessionReader reader, final TerminatedUserSessionWriter writer) {
    this.reader = reader;
    this.writer = writer;
  }

  @Override
  public void save(final TerminatedSession session) {
    writer.writeTerminatedUserSession(new TerminatedUserSessionDto(session.sessionId()));
  }

  @Override
  public boolean exists(final String sessionId) {
    return reader.exists(sessionId);
  }

  @Override
  public void deleteOlderThan(final Instant cutoff) {
    writer.deleteTerminatedUserSessionsOlderThan(OffsetDateTime.ofInstant(cutoff, ZoneOffset.UTC));
  }
}
