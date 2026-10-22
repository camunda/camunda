/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.qa;

import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.nio.file.Path;

/** Brokers with the Event Plane on, publishing fast into a bucket, and load for them. */
final class EventPlaneBrokers {

  static final String PREFIX = "zeebe.broker.experimental.eventPlane.";

  private EventPlaneBrokers() {}

  /** Publishes into a bucket on the filesystem, a segment a second. */
  static TestStandaloneBroker withEventPlane(final TestStandaloneBroker broker, final Path bucket) {
    return broker
        .withProperty(PREFIX + "enabled", true)
        .withProperty(PREFIX + "store.type", "filesystem")
        .withProperty(PREFIX + "store.path", bucket.toAbsolutePath().toString())
        .withProperty(PREFIX + "cutInterval", "1s")
        .withProperty(PREFIX + "pollInterval", "100ms");
  }

  static void publishMessages(final TestStandaloneBroker broker, final int count) {
    try (final var client = broker.newClientBuilder().build()) {
      for (int i = 0; i < count; i++) {
        client
            .newPublishMessageCommand()
            .messageName("event-plane")
            .correlationKey("key-" + i)
            .send()
            .join();
      }
    }
  }
}
