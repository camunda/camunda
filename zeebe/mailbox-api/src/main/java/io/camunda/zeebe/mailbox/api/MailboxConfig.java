/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox.api;

import java.time.Duration;

/**
 * Settings of the mailbox. The values of {@link #defaults()} are starting points that have to be
 * measured.
 *
 * @param minPollInterval how long a poller waits after a poll that found work
 * @param maxPollInterval the longest wait of a poller that finds nothing
 * @param claimTimeout how long a claim stays valid before another claimer may take the messages
 * @param batchSize the most messages that one claim returns
 * @param inboxTtl how long an unprocessed inbox message is kept
 * @param outboxTtl how long an uncollected response is kept
 */
public record MailboxConfig(
    Duration minPollInterval,
    Duration maxPollInterval,
    Duration claimTimeout,
    int batchSize,
    Duration inboxTtl,
    Duration outboxTtl) {

  public MailboxConfig {
    requirePositive("minPollInterval", minPollInterval);
    requirePositive("maxPollInterval", maxPollInterval);
    requirePositive("claimTimeout", claimTimeout);
    requirePositive("inboxTtl", inboxTtl);
    requirePositive("outboxTtl", outboxTtl);
    if (maxPollInterval.compareTo(minPollInterval) < 0) {
      throw new IllegalArgumentException("maxPollInterval must not be below minPollInterval");
    }
    if (batchSize <= 0) {
      throw new IllegalArgumentException("batchSize must be positive");
    }
  }

  public static MailboxConfig defaults() {
    return new MailboxConfig(
        Duration.ofMillis(10),
        Duration.ofMillis(500),
        Duration.ofSeconds(30),
        100,
        Duration.ofMinutes(10),
        Duration.ofMinutes(10));
  }

  private static void requirePositive(final String name, final Duration value) {
    if (value.isZero() || value.isNegative()) {
      throw new IllegalArgumentException(name + " must be positive");
    }
  }
}
