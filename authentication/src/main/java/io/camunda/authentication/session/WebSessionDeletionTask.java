/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebSessionDeletionTask implements Runnable {

  public static final int DELETE_EXPIRED_SESSIONS_DELAY = 1_000 * 60 * 30;
  private static final Logger LOGGER = LoggerFactory.getLogger(WebSessionDeletionTask.class);
  private final WebSessionRepository webSessionRepository;

  public WebSessionDeletionTask(final WebSessionRepository webSessionRepository) {
    this.webSessionRepository = webSessionRepository;
  }

  @Override
  public void run() {
    try {
      webSessionRepository.deleteExpiredWebSessions();
    } catch (final Exception e) {
      LOGGER.warn("Failed to delete expired web session: {}", e.getMessage(), e);
    }
  }
}
