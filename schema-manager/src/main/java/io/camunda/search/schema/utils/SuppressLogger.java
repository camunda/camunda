/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema.utils;

import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Set;
import org.slf4j.Logger;

// Downgrade transient connection issues to ElasticSearch/OpenSearch to a WARN
public class SuppressLogger {
  private static final Set<Class<? extends Exception>> SUPPRESSED_EXCEPTIONS =
      Set.of(SocketTimeoutException.class, ConnectException.class, SocketException.class);
  private final Logger logger;

  public SuppressLogger(final Logger logger) {
    this.logger = logger;
  }

  public void error(final String message, final Throwable t) {
    if (SUPPRESSED_EXCEPTIONS.contains(t.getClass())) {
      logger.warn("[downgraded from ERROR]: {} ", message, t);
    } else {
      logger.error(message, t);
    }
  }

  public void debug(final String message, final Object... args) {
    logger.debug(message, args);
  }

  public void warn(final String message, final Object... args) {
    logger.warn(message, args);
  }
}
