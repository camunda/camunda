/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox;

import io.camunda.zeebe.mailbox.api.CleanupResult;
import io.camunda.zeebe.mailbox.api.MailboxConfig;
import io.camunda.zeebe.mailbox.api.MailboxStore;
import io.camunda.zeebe.mailbox.api.MailboxStoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Removes messages and responses that nobody collected in time. The caller decides how often to
 * call {@link #cleanOnce()}. A failure is logged and left for the next call.
 */
public final class MailboxCleaner {

  private static final Logger LOG = LoggerFactory.getLogger(MailboxCleaner.class);

  private final MailboxStore store;
  private final MailboxConfig config;

  public MailboxCleaner(final MailboxStore store, final MailboxConfig config) {
    this.store = store;
    this.config = config;
  }

  public CleanupResult cleanOnce() {
    try {
      return store.deleteExpired(config.inboxTtl(), config.outboxTtl());
    } catch (final MailboxStoreException e) {
      LOG.warn("Could not clean up the mailbox", e);
      return new CleanupResult(0, 0);
    }
  }
}
