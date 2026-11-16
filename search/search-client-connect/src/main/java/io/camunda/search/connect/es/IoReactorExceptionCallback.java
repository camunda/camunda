/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.connect.es;

import java.nio.channels.ClosedSelectorException;
import org.apache.hc.core5.function.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Replaces the Apache HttpClient default, which logs every I/O reactor exception at ERROR.
 *
 * <p>Closing the client races with the reactor thread terminating: the closing thread can give up
 * waiting (timeout or interrupt) and close the selector and connection pool while the reactor
 * thread is still releasing its channels. That surfaces as {@link ClosedSelectorException} or an
 * {@link IllegalStateException} from the pool, both harmless as the client is already closed, so
 * they are logged at DEBUG. Anything else is still logged at ERROR.
 */
public final class IoReactorExceptionCallback implements Callback<Exception> {

  static final String POOL_ENTRY_MISSING_MESSAGE =
      "Pool entry is not present in the set of leased entries";

  // same logger name as the Apache default callback, so existing log config keeps matching
  private static final Logger LOGGER =
      LoggerFactory.getLogger("org.apache.hc.client5.http.impl.async");

  @Override
  public void execute(final Exception exception) {
    if (isShutdownRace(exception)) {
      LOGGER.debug(
          "Ignoring I/O reactor exception caused by concurrent client shutdown", exception);
    } else {
      LOGGER.error(exception.getMessage(), exception);
    }
  }

  static boolean isShutdownRace(final Exception exception) {
    return exception instanceof ClosedSelectorException
        || (exception instanceof IllegalStateException
            && POOL_ENTRY_MISSING_MESSAGE.equals(exception.getMessage()));
  }
}
