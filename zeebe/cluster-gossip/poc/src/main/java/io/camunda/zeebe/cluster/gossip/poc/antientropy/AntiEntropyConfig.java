/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.cluster.gossip.poc.antientropy;

import java.time.Duration;

/**
 * Timing of the anti-entropy transport. The defaults mirror SWIM's defaults (gossip every 250 ms to
 * 2 peers, full sync every 10 s), so that the two proofs of concept are compared on equal terms.
 *
 * @param pushInterval how often changes collected since the last push are sent
 * @param fanout to how many random peers each push goes
 * @param retransmits in how many consecutive pushes a change is sent; one push to a few random
 *     peers misses some members often enough to fall back to the slow full sync
 * @param syncInterval how often the full state is exchanged with one random peer
 * @param syncTimeout how long to wait for a peer's reply to a sync
 */
public record AntiEntropyConfig(
    Duration pushInterval,
    int fanout,
    int retransmits,
    Duration syncInterval,
    Duration syncTimeout) {

  public static AntiEntropyConfig defaults() {
    return new AntiEntropyConfig(
        Duration.ofMillis(250), 2, 3, Duration.ofSeconds(10), Duration.ofSeconds(5));
  }
}
