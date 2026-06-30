/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.fact;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

final class EventBridgeFactPublisherTest {

  @Test
  void shouldRouteToOneBasedPartitionWithinRange() {
    // when / then — partitions are 1-based and within [1, partitionCount]
    for (long definitionKey = 0; definitionKey < 1000; definitionKey++) {
      final int partition = EventBridgeFactPublisher.partitionFor(definitionKey, 3);
      assertThat(partition).isBetween(1, 3);
    }
  }

  @Test
  void shouldRouteSameDefinitionToSamePartition() {
    // then — routing is deterministic, so a definition's facts stay co-located
    assertThat(EventBridgeFactPublisher.partitionFor(77L, 3))
        .isEqualTo(EventBridgeFactPublisher.partitionFor(77L, 3));
  }
}
