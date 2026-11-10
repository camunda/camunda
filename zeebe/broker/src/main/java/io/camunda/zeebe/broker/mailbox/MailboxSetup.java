/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.mailbox;

import io.camunda.zeebe.mailbox.api.MailboxConfig;
import io.camunda.zeebe.mailbox.api.MailboxStore;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.jspecify.annotations.Nullable;

/**
 * What the broker needs to use the mailbox.
 *
 * @param store the storage of the mailbox. Shared by all partitions of the broker.
 * @param config the settings of the mailbox
 * @param simulatedZone for tests and a proof of concept in one network. If set, the leaders of
 *     other zones count as out of reach, as they are when the regions are separated. Null in a real
 *     deployment, where gossip does not cross the border between regions.
 * @param senderExecutor runs the inserts into the inbox. The store may block, so these must not run
 *     on an actor thread.
 */
public record MailboxSetup(
    MailboxStore store,
    MailboxConfig config,
    @Nullable String simulatedZone,
    Executor senderExecutor) {

  /** A setup whose inserts run on a pool of daemon threads that the setup creates. */
  public static MailboxSetup of(
      final MailboxStore store, final MailboxConfig config, final @Nullable String simulatedZone) {
    return new MailboxSetup(
        store,
        config,
        simulatedZone,
        Executors.newCachedThreadPool(
            runnable -> {
              final var thread = new Thread(runnable, "mailbox-sender");
              thread.setDaemon(true);
              return thread;
            }));
  }
}
