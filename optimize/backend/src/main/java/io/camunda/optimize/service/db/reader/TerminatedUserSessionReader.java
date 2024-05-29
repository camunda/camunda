/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.db.reader;

import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class TerminatedUserSessionReader {

  public boolean exists(final String sessionId) {
    log.debug("Fetching terminated user session with id [{}]", sessionId);
    try {
      return sessionIdExists(sessionId);
    } catch (Exception e) {
      throw new OptimizeRuntimeException(
          "Was not able to check for terminated session existence!", e);
    }
  }

  protected abstract boolean sessionIdExists(final String sessionId) throws IOException;
}
