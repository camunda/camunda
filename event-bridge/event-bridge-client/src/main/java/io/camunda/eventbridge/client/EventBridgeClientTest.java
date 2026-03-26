/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.client;

import java.util.Scanner;

public class EventBridgeClientTest {

  public static void main(final String[] args) throws Exception {

    final var client = EventBridgeClient.create("http://localhost:8080");

    final var consumer1 = client.subscribe("cam", "foo").join();

    Thread.sleep(5000);

    final var consumer2 = client.subscribe("cam", "bar").join();

    Thread.sleep(5000);

    final var consumer3 = client.subscribe("cam", "rab").join();

    Thread.sleep(15000);
    consumer2.leaveGroup().join();

    Thread.sleep(15000);
    consumer2.joinGroup().join();

    waitUntilSystemInput("exit");
  }

  private static void waitUntilSystemInput(final String exitCode) {
    try (final Scanner scanner = new Scanner(System.in)) {
      while (scanner.hasNextLine()) {
        final String nextLine = scanner.nextLine();
        if (nextLine.contains(exitCode)) {
          return;
        }
      }
    }
  }
}
