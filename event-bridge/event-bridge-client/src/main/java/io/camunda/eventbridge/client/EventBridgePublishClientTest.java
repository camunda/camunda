/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.client;

public class EventBridgePublishClientTest {

  public static void main(final String[] args) {

    //    try (final var client = new EventBridgePublishClient("http://localhost:8080")) {
    //
    //      for (int i = 0; i < 10000; i++) {
    //        // Build records separately
    //        final var entry1 =
    //            new EventBridgeEntryBuilder().key("device-1").value("foo".getBytes()).build();
    //
    //        final var entry2 =
    //            new EventBridgeEntryBuilder().key("device-2").value("bar".getBytes()).build();
    //
    //        // Publish as raw entries
    //        final var result =
    // client.newBatch().addEntry(entry1).addEntry(entry2).publish(1).join();
    //
    //        System.out.println(result);
    //      }
    //    }

    try (final var client1 = new EventBridgePublishClient("http://localhost:8080")) {
      final var fetchResult = client1.fetch(1, 1, 1024).join();

      for (final var entry : fetchResult.entries(1)) {
        System.out.println("Position: " + entry.getPosition());
        System.out.println("Value: " + new String(entry.getValueCopy()));
      }

      // Track progress
      final long nextOffset = fetchResult.nextOffset(1);
      final long lag = fetchResult.lag();
    }
  }
}
