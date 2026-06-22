/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.client;

import java.nio.charset.StandardCharsets;

/**
 * Manual POC driver. Talks to a single gateway ({@code http://localhost:8080}) and exercises every
 * partition — in a multi-broker cluster the gateway transparently routes each partition to its
 * leader, so this also validates cross-broker publish + fetch.
 */
public final class EventBridgeClientTest {

  private EventBridgeClientTest() {}

  public static void main(final String[] args) throws Exception {
    final var client = EventBridgeClient.create("http://localhost:8080");
    final int partitions = args.length > 0 ? Integer.parseInt(args[0]) : 2;

    for (int p = 1; p <= partitions; p++) {
      final var positions =
          client
              .newBatch()
              .add("order-" + p + "-a", ("hello-from-p" + p).getBytes(StandardCharsets.UTF_8))
              .add("order-" + p + "-b", ("world-from-p" + p).getBytes(StandardCharsets.UTF_8))
              .publish(p)
              .join();
      System.out.println("[p" + p + "] published positions: " + positions);
    }

    for (int p = 1; p <= partitions; p++) {
      final var fetch = client.fetch(p, 0, 64 * 1024).join();
      System.out.println(
          "[p"
              + p
              + "] fetch success="
              + fetch.isSuccess()
              + " firstPos="
              + fetch.firstBatchPosition()
              + " lastPos="
              + fetch.lastBatchPosition()
              + " hw="
              + fetch.highWatermark());
      for (final var entry : fetch.entries(0)) {
        System.out.println(
            "    p"
                + p
                + "@"
                + entry.getPosition()
                + " = "
                + new String(entry.getValueCopy(), StandardCharsets.UTF_8));
      }
    }
  }
}
