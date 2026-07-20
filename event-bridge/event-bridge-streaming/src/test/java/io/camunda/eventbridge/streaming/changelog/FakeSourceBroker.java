/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.changelog;

import java.util.ArrayList;
import java.util.List;

/**
 * A minimal in-memory stand-in for the source topic's single partition, shared between two members'
 * mocked {@link io.camunda.eventbridge.client.Consumer}s in a multi-member failover test — plus the
 * coordinator's ownership epoch for it (event-bridge-streaming ADR 0009 §4's interim halt
 * discipline): a member's {@code commitOffset} is rejected with {@link
 * io.camunda.eventbridge.client.ConsumerNotRegisteredException} once a successor's promotion has
 * fenced the epoch it captured past what is now current — the same rejection a real coordinator-
 * epoch fence produces.
 */
final class FakeSourceBroker {

  private final List<byte[]> log = new ArrayList<>();
  private volatile long ownerEpoch = 0;

  /** Appends one record, returning its assigned offset. */
  synchronized long append(final byte[] value) {
    log.add(value);
    return log.size() - 1L;
  }

  /** The number of records appended so far (the exclusive upper bound of valid offsets). */
  synchronized int size() {
    return log.size();
  }

  /** Records from {@code fromOffsetInclusive}, up to {@code max} of them. */
  synchronized List<byte[]> from(final long fromOffsetInclusive, final int max) {
    final List<byte[]> result = new ArrayList<>();
    for (long o = fromOffsetInclusive; o < log.size() && result.size() < max; o++) {
      result.add(log.get((int) o));
    }
    return result;
  }

  /** The epoch a member must hold for its {@code commitOffset} to be accepted. */
  long ownerEpoch() {
    return ownerEpoch;
  }

  /**
   * Fences the current owner: bumps the epoch, so any member still stamping the old one has its
   * next {@code commitOffset} rejected — the trigger for the halt discipline (streaming ADR 0009
   * §4).
   */
  long fence() {
    return ++ownerEpoch;
  }
}
