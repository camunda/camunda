/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.reader;

import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.io.IOException;
import org.slf4j.Logger;

public abstract class TerminatedUserSessionReader {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(TerminatedUserSessionReader.class);

  public boolean exists(final String sessionId) {
    log.debug("Fetching terminated user session with id [{}]", sessionId);
    try {
      return sessionIdExists(sessionId);
    } catch (final Exception e) {
      throw new OptimizeRuntimeException(
          "Was not able to check for terminated session existence!", e);
    }
  }

  protected abstract boolean sessionIdExists(final String sessionId) throws IOException;
}
