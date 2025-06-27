/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.listeners;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.context.ApplicationListener;

public class ApplicationErrorListener implements ApplicationListener<ApplicationFailedEvent> {

  private static final Logger LOGGER = LoggerFactory.getLogger("io.camunda.application");

  @Override
  public void onApplicationEvent(final ApplicationFailedEvent event) {
    final var exception = event.getException();
    final var message = exception.getMessage();
    if (ExceptionUtils.indexOfThrowable(exception, InterruptedException.class) != -1) {
      LOGGER.warn("Startup interrupted. Message: {}", message);
      LOGGER.debug("Stack trace:", exception);
      Thread.currentThread().interrupt();
      event.getApplicationContext().close();
      return; // Skip System.exit(-1) to avoid CrashLoopBackOff on "expected" shutdown
    }

    LOGGER.error("Failed to start application with message: {}", message, exception);

    event.getApplicationContext().close();
    System.exit(-1);
  }
}
