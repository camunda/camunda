/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.examples;

import io.camunda.eventbridge.client.EventBridgeClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

/**
 * Manual example driver. Talks to a single gateway ({@code http://localhost:8080}) and exercises
 * every partition of a topic — in a multi-broker cluster the gateway transparently routes each
 * partition to its leader, so this also validates cross-broker publish + fetch.
 *
 * <p>Usage: {@code PublishFetchExample [topic] [partitionCount]}. The topic is created if it does
 * not already exist (it may also be auto-created via {@code event-bridge.topics}); publishing then
 * retries briefly while the topic's Raft group finishes provisioning.
 */
public final class PublishFetchExample {

  private PublishFetchExample() {}

  public static void main(final String[] args) throws Exception {
    final var client = EventBridgeClient.create("http://localhost:8080");
    final var topic = args.length > 0 ? args[0] : "example-topic";
    final int partitions = args.length > 1 ? Integer.parseInt(args[1]) : 2;

    ensureTopic(client, topic, partitions);

    for (int p = 1; p <= partitions; p++) {
      final int partition = p;
      final var positions =
          retry(
              () ->
                  client
                      .newBatch()
                      .add(
                          "order-" + partition + "-a",
                          ("hello-from-p" + partition).getBytes(StandardCharsets.UTF_8))
                      .add(
                          "order-" + partition + "-b",
                          ("world-from-p" + partition).getBytes(StandardCharsets.UTF_8))
                      .publishToTopic(topic, partition));
      System.out.println("[" + topic + "/p" + p + "] published positions: " + positions);
    }

    for (int p = 1; p <= partitions; p++) {
      final var fetch = client.fetchFromTopic(topic, p, 0, 64 * 1024).join();
      System.out.println(
          "["
              + topic
              + "/p"
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
                + entry.position()
                + " = "
                + new String(entry.value(), StandardCharsets.UTF_8));
      }
    }
  }

  /** Creates the topic if absent (an already-existing topic is fine). */
  private static void ensureTopic(
      final EventBridgeClient client, final String topic, final int partitions) {
    try {
      client.createTopic(topic, partitions, 1).join();
      System.out.println("Created topic " + topic + " (" + partitions + " partitions)");
    } catch (final CompletionException e) {
      System.out.println("Topic " + topic + " already present (" + e.getCause().getMessage() + ")");
    }
  }

  /** Joins {@code op}, retrying briefly so a just-created topic has time to elect its leaders. */
  private static <T> T retry(final Supplier<java.util.concurrent.CompletableFuture<T>> op)
      throws InterruptedException {
    RuntimeException last = null;
    for (int attempt = 0; attempt < 20; attempt++) {
      try {
        return op.get().join();
      } catch (final RuntimeException e) {
        last = e;
        Thread.sleep(500);
      }
    }
    throw last;
  }
}
