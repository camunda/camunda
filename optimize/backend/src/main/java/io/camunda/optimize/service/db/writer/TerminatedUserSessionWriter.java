/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.writer;

import io.camunda.optimize.dto.optimize.query.TerminatedUserSessionDto;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.io.IOException;
import java.time.OffsetDateTime;
import org.slf4j.Logger;

public abstract class TerminatedUserSessionWriter {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(TerminatedUserSessionWriter.class);

  public void writeTerminatedUserSession(final TerminatedUserSessionDto sessionDto) {
    log.debug("Writing terminated user session with id [{}] to database.", sessionDto.getId());
    try {
      performWritingTerminatedUserSession(sessionDto);
    } catch (final IOException e) {
      final String message = "Could not write terminated user sessions to database.";
      log.error(message, e);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  public void deleteTerminatedUserSessionsOlderThan(final OffsetDateTime timestamp) {
    log.debug("Deleting terminated user session older than [{}] to database.", timestamp);
    try {
      performDeleteTerminatedUserSessionOlderThan(timestamp);
    } catch (final IOException e) {
      final String message =
          String.format("Could not delete user sessions older than [%s] from database", timestamp);
      log.error(message, e);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  protected abstract void performWritingTerminatedUserSession(
      final TerminatedUserSessionDto sessionDto) throws IOException;

  protected abstract void performDeleteTerminatedUserSessionOlderThan(
      final OffsetDateTime timestamp) throws IOException;
}
