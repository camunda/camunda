/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.client;

import io.camunda.eventbridge.protocol.EventBridgeRecordBuilder;

public class EventBridgePublishClientTest {

  public static void main(final String[] args) {

    try (final var client = new EventBridgePublishClient("http://localhost:8080")) {
      // Build records separately
      final var record1 =
          new EventBridgeRecordBuilder().key("device-1").payload("foo".getBytes()).build();

      final var record2 =
          new EventBridgeRecordBuilder().key("device-2").payload("bar".getBytes()).build();

      // Publish as raw entries
      final var result = client.newBatch().addEntry(record1).addEntry(record2).publish(0).join();

      System.out.println(result);
    }
  }
}
