/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.cluster.gossip.poc.core;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Counters that let the proofs of concept be compared. Only what a transport can observe itself is
 * counted: the anti-entropy transport counts every message it sends, while the SWIM transport can
 * only report how large the member state is that SWIM gossips on each change.
 */
public final class GossipStats {

  private final AtomicLong messagesSent = new AtomicLong();
  private final AtomicLong bytesSent = new AtomicLong();
  private final AtomicLong publishedStateBytes = new AtomicLong();

  public void recordMessage(final int bytes) {
    messagesSent.incrementAndGet();
    bytesSent.addAndGet(bytes);
  }

  public void recordPublishedState(final long bytes) {
    publishedStateBytes.set(bytes);
  }

  /** Messages this member sent itself; not counted by the SWIM transport. */
  public long messagesSent() {
    return messagesSent.get();
  }

  /** Bytes this member sent itself; not counted by the SWIM transport. */
  public long bytesSent() {
    return bytesSent.get();
  }

  /**
   * Size of the state SWIM gossips for this member on every change: all of its properties,
   * including those not written by the gossiper. Zero for the anti-entropy transport.
   */
  public long publishedStateBytes() {
    return publishedStateBytes.get();
  }
}
