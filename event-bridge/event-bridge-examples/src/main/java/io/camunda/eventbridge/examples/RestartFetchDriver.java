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

/**
 * Manual POC driver to verify produce + fetch (including mid-batch offsets) across a restart.
 *
 * <p>Usage (gateway via {@code -Dgateway=http://localhost:8080}, topic via {@code -Dtopic=...},
 * default {@code restart-topic}):
 *
 * <ul>
 *   <li>{@code produce <partition> <nEvents>} — publish one batch of nEvents to the topic partition
 *   <li>{@code fetch <partition> <offset>} — fetch from offset and print header + entries
 * </ul>
 */
public final class RestartFetchDriver {

  private RestartFetchDriver() {}

  public static void main(final String[] args) throws Exception {
    final var gateway = System.getProperty("gateway", "http://localhost:8080");
    final var topic = System.getProperty("topic", "restart-topic");
    final var client = EventBridgeClient.create(gateway);
    final var mode = args.length > 0 ? args[0] : "fetch";

    switch (mode) {
      case "produce" -> {
        final int partition = args.length > 1 ? Integer.parseInt(args[1]) : 1;
        final int n = args.length > 2 ? Integer.parseInt(args[2]) : 3;
        ensureTopic(client, topic, partition);
        final var batch = client.newBatch();
        for (int i = 0; i < n; i++) {
          batch.add("key-" + i, ("event-" + i).getBytes(StandardCharsets.UTF_8));
        }
        final var positions = batch.publishToTopic(topic, partition).join();
        System.out.println(
            "PRODUCED topic=" + topic + " partition=" + partition + " positions=" + positions);
      }
      case "fetch" -> {
        final int partition = args.length > 1 ? Integer.parseInt(args[1]) : 1;
        final long offset = args.length > 2 ? Long.parseLong(args[2]) : 1;
        final var f = client.fetchFromTopic(topic, partition, offset, 256 * 1024).join();
        System.out.println(
            "FETCH topic="
                + topic
                + " partition="
                + partition
                + " offset="
                + offset
                + " success="
                + f.isSuccess()
                + " empty="
                + f.isEmpty()
                + " firstPos="
                + f.firstBatchPosition()
                + " lastPos="
                + f.lastBatchPosition()
                + " hw="
                + f.highWatermark()
                + " outcome="
                + f.outcome());
        int count = 0;
        for (final var e : f.entries(0)) {
          System.out.println(
              "  @"
                  + e.getPosition()
                  + " = "
                  + new String(e.getValueCopy(), StandardCharsets.UTF_8));
          count++;
        }
        System.out.println("  entries=" + count);
      }
      default -> System.out.println("unknown mode: " + mode);
    }
  }

  /** Creates the topic if absent so produce has a target across restarts. */
  private static void ensureTopic(
      final EventBridgeClient client, final String topic, final int partitions) {
    try {
      client.createTopic(topic, partitions, 1).join();
      System.out.println("Created topic " + topic + " (" + partitions + " partitions)");
    } catch (final CompletionException e) {
      System.out.println("Topic " + topic + " already present (" + e.getCause().getMessage() + ")");
    }
  }
}
