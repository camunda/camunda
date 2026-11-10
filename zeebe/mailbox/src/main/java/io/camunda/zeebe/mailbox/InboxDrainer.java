/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox;

import io.camunda.zeebe.mailbox.api.InboxMessage;
import io.camunda.zeebe.mailbox.api.MailboxConfig;
import io.camunda.zeebe.mailbox.api.MailboxStore;
import io.camunda.zeebe.mailbox.api.MailboxStoreException;
import io.camunda.zeebe.mailbox.api.MessageKind;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Moves the messages of one partition inbox to a writer. The leader of the partition runs it.
 *
 * <p>A message is acknowledged, and so removed from the inbox, only after the writer reports that
 * it is written. If the process stops in between, the claim times out and the next claimer gets the
 * message again. So a message is delivered at least once, and a writer must expect repeats.
 *
 * <p>The class does no scheduling. Call {@link #drainOnce()} repeatedly, and wait between the calls
 * as the {@link AdaptiveBackoff} says. Not thread-safe.
 */
public final class InboxDrainer {

  private static final Logger LOG = LoggerFactory.getLogger(InboxDrainer.class);

  private final MailboxStore store;
  private final int partitionId;
  private final String claimerId;
  private final MailboxConfig config;
  private final InboxMessageWriter writer;

  private final Set<MessageKind> kinds;

  /**
   * Drains messages of every kind.
   *
   * @param claimerId identifies this node as the claimer, for example its broker id
   */
  public InboxDrainer(
      final MailboxStore store,
      final int partitionId,
      final String claimerId,
      final MailboxConfig config,
      final InboxMessageWriter writer) {
    this(store, partitionId, claimerId, EnumSet.allOf(MessageKind.class), config, writer);
  }

  /**
   * @param claimerId identifies this drainer as the claimer. Drainers that run side by side on one
   *     inbox need different ids, because releasing the claims works for an id.
   * @param kinds the kinds of messages this drainer takes. Other drainers take the rest.
   */
  public InboxDrainer(
      final MailboxStore store,
      final int partitionId,
      final String claimerId,
      final Set<MessageKind> kinds,
      final MailboxConfig config,
      final InboxMessageWriter writer) {
    this.store = store;
    this.partitionId = partitionId;
    this.claimerId = claimerId;
    this.kinds = kinds;
    this.config = config;
    this.writer = writer;
  }

  /** Takes one batch from the inbox, writes it, and acknowledges what was written. */
  public DrainResult drainOnce() {
    final List<InboxMessage> claimed;
    try {
      claimed =
          store.claim(partitionId, claimerId, kinds, config.batchSize(), config.claimTimeout());
    } catch (final MailboxStoreException e) {
      LOG.warn("Could not claim messages of partition {}", partitionId, e);
      return new DrainResult(0, 0, true);
    }
    if (claimed.isEmpty()) {
      return new DrainResult(0, 0, false);
    }

    var failed = false;
    var written = 0;
    try {
      written = Math.max(0, Math.min(writer.writeAll(claimed), claimed.size()));
    } catch (final RuntimeException e) {
      LOG.warn("Could not write messages of partition {}", partitionId, e);
      failed = true;
    }

    if (written > 0) {
      failed |= !acknowledge(claimed.subList(0, written));
    }
    if (failed || written < claimed.size()) {
      releaseClaims();
    }
    return new DrainResult(claimed.size(), written, failed);
  }

  /**
   * Gives up the claims of this node, so that the next leader can take the messages at once. Call
   * it when this node stops leading the partition.
   */
  public void releaseClaims() {
    try {
      store.releaseClaims(partitionId, claimerId);
    } catch (final MailboxStoreException e) {
      LOG.warn("Could not release the claims on partition {}", partitionId, e);
    }
  }

  private boolean acknowledge(final List<InboxMessage> written) {
    try {
      store.acknowledge(
          partitionId, claimerId, written.stream().map(InboxMessage::sequence).toList());
      return true;
    } catch (final MailboxStoreException e) {
      LOG.warn("Could not acknowledge messages of partition {}", partitionId, e);
      return false;
    }
  }
}
