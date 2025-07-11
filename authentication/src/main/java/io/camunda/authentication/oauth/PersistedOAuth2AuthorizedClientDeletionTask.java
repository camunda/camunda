/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.oauth;

import io.camunda.search.clients.PersistentOAuth2AuthorizedClientsClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PersistedOAuth2AuthorizedClientDeletionTask implements Runnable {

  public static final int DELETE_EXPIRED_AUTHORIZED_CLIENTS_DELAY = 1_000 * 60 * 30;
  private static final Logger LOGGER =
      LoggerFactory.getLogger(PersistedOAuth2AuthorizedClientDeletionTask.class);
  private final PersistentOAuth2AuthorizedClientsClient oauth2AuthorizedClientsClient;

  public PersistedOAuth2AuthorizedClientDeletionTask(
      final PersistentOAuth2AuthorizedClientsClient oauth2AuthorizedClientsClient) {
    this.oauth2AuthorizedClientsClient = oauth2AuthorizedClientsClient;
  }

  @Override
  public void run() {
    try {
      oauth2AuthorizedClientsClient.removeExpiredAuthorizedClients();
    } catch (final Exception e) {
      LOGGER.warn("Failed to delete expired authorized clients: {}", e.getMessage(), e);
    }
  }
}
