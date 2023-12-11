/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.writer;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.TerminatedUserSessionDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;

import java.io.IOException;
import java.time.OffsetDateTime;

@Slf4j
public abstract class TerminatedUserSessionWriter {

  public void writeTerminatedUserSession(final TerminatedUserSessionDto sessionDto) {
    log.debug("Writing terminated user session with id [{}] to database.", sessionDto.getId());
    try {
      performWritingTerminatedUserSession(sessionDto);
    } catch (IOException e) {
      String message = "Could not write terminated user sessions to database.";
      log.error(message, e);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  public void deleteTerminatedUserSessionsOlderThan(final OffsetDateTime timestamp) {
    log.debug("Deleting terminated user session older than [{}] to database.", timestamp);
    try {
      performDeleteTerminatedUserSessionOlderThan(timestamp);
    } catch (IOException e) {
      String message = String.format("Could not delete user sessions older than [%s] from database", timestamp);
      log.error(message, e);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  protected abstract void performWritingTerminatedUserSession(final TerminatedUserSessionDto sessionDto) throws IOException;

  protected abstract void performDeleteTerminatedUserSessionOlderThan(final OffsetDateTime timestamp) throws IOException;

}
