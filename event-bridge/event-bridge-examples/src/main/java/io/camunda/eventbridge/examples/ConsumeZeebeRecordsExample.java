/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.examples;

import io.camunda.eventbridge.client.EventBridgeClient;
import io.camunda.eventbridge.zeebe.connector.ZeebeRecordListener;
import java.util.List;

/**
 * Consumes Zeebe records from an Event Bridge topic and prints them.
 *
 * <p>Run against a node with the topic already produced to (e.g. by the {@code ZeebeRecordExporter}
 * or a producer):
 *
 * <pre>
 *   java io.camunda.eventbridge.examples.ConsumeZeebeRecordsExample [topic] [group]
 *   -Dgateway=http://localhost:8080
 * </pre>
 */
public final class ConsumeZeebeRecordsExample {

  private ConsumeZeebeRecordsExample() {}

  public static void main(final String[] args) throws Exception {
    final var gateway = System.getProperty("gateway", "http://localhost:8080");
    final var topic = args.length > 0 ? args[0] : "zeebe-records";
    final var group = args.length > 1 ? args[1] : "example-consumer";

    final var client = EventBridgeClient.create(gateway);

    try (var listener =
        ZeebeRecordListener.builder(client)
            .group(group)
            .topics(List.of(topic))
            .onRecord(
                record ->
                    System.out.printf(
                        "%-22s %-10s partition=%d key=%d %s%n",
                        record.getValueType(),
                        record.getIntent(),
                        record.getPartitionId(),
                        record.getKey(),
                        record.getValue()))
            .start()) {

      System.out.printf(
          "Consuming Zeebe records from topic '%s' (group '%s'); Ctrl-C to stop.%n", topic, group);
      Thread.currentThread().join();
    } finally {
      client.close();
    }
  }
}
