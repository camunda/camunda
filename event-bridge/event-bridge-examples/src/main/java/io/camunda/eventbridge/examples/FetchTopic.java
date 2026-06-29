/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.examples;

import io.camunda.eventbridge.client.EventBridgeClient;

/**
 * Diagnostic: directly fetches each partition of a topic and prints how many entries are present —
 * independent of any consumer group. Used by the smoke test to confirm the exporter published.
 *
 * <pre>
 *   java io.camunda.eventbridge.examples.FetchTopic [topic] [partitionCount] -Dgateway=http://localhost:8080
 * </pre>
 */
public final class FetchTopic {

  private FetchTopic() {}

  public static void main(final String[] args) {
    final var gateway = System.getProperty("gateway", "http://localhost:8080");
    final var topic = args.length > 0 ? args[0] : "zeebe-records";
    final int partitions = args.length > 1 ? Integer.parseInt(args[1]) : 3;

    try (final var client = EventBridgeClient.create(gateway)) {
      long total = 0;
      for (int partition = 1; partition <= partitions; partition++) {
        final var result = client.fetchFromTopic(topic, partition, 0, 1 << 20).join();
        long count = 0;
        for (final var ignored : result.entries()) {
          count++;
        }
        total += count;
        System.out.printf(
            "TOPIC %s partition=%d success=%s highWatermark=%d entries=%d%n",
            topic, partition, result.isSuccess(), result.highWatermark(), count);
      }
      System.out.println("TOTAL entries across topic: " + total);
    }
  }
}
