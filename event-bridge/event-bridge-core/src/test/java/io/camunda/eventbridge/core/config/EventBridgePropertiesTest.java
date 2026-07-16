/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.eventbridge.core.config.EventBridgeProperties.BrokerProperties;
import io.camunda.eventbridge.core.config.EventBridgeProperties.RaftProperties;
import io.camunda.eventbridge.core.config.EventBridgeProperties.TopicProperties;
import io.camunda.eventbridge.core.topic.AutoCreatedTopic;
import java.util.List;
import org.junit.jupiter.api.Test;

final class EventBridgePropertiesTest {

  @Test
  void shouldDefaultTopicsToEmptyWhenUnset() {
    // given / when
    final var properties = propertiesWith(null, null, null);

    // then
    assertThat(properties.topics()).isEmpty();
    assertThat(properties.resolvedTopics()).isEmpty();
  }

  @Test
  void shouldUseExplicitPartitionCountAndReplicationFactor() {
    // given
    final var topics = List.of(new TopicProperties("orders", 6, 3, null));

    // when
    final var resolved = propertiesWith(topics, 1, 1).resolvedTopics();

    // then
    assertThat(resolved).containsExactly(new AutoCreatedTopic("orders", 6, 3, "DELETE"));
  }

  @Test
  void shouldFallBackToBrokerAndRaftDefaultsWhenUnset() {
    // given — neither count is set on the topic
    final var topics = List.of(new TopicProperties("orders", null, null, null));

    // when — broker.partitionCount=4, raft.replicationFactor=2
    final var resolved = propertiesWith(topics, 4, 2).resolvedTopics();

    // then
    assertThat(resolved).containsExactly(new AutoCreatedTopic("orders", 4, 2, "DELETE"));
  }

  @Test
  void shouldResolveEachTopicIndependently() {
    // given
    final var topics =
        List.of(
            new TopicProperties("explicit", 2, 3, null),
            new TopicProperties("defaulted", null, null, null),
            new TopicProperties("partial", 5, null, null));

    // when
    final var resolved = propertiesWith(topics, 4, 1).resolvedTopics();

    // then — explicit kept, missing values fall back to broker/raft defaults
    assertThat(resolved)
        .containsExactly(
            new AutoCreatedTopic("explicit", 2, 3, "DELETE"),
            new AutoCreatedTopic("defaulted", 4, 1, "DELETE"),
            new AutoCreatedTopic("partial", 5, 1, "DELETE"));
  }

  @Test
  void shouldDefaultCleanupPolicyToDeleteWhenUnset() {
    // given — a topic configured with no cleanupPolicy at all (today's config, unaffected)
    final var topics = List.of(new TopicProperties("orders", 3, 1, null));

    // when
    final var resolved = propertiesWith(topics, 1, 1).resolvedTopics();

    // then
    assertThat(resolved).extracting(AutoCreatedTopic::cleanupPolicy).containsExactly("DELETE");
  }

  @Test
  void shouldResolveAnExplicitCompactCleanupPolicy() {
    // given — a topic explicitly configured for latest-per-key retention (event-bridge ADR 0001)
    final var topics = List.of(new TopicProperties("changelog", 3, 1, "COMPACT"));

    // when
    final var resolved = propertiesWith(topics, 1, 1).resolvedTopics();

    // then
    assertThat(resolved).containsExactly(new AutoCreatedTopic("changelog", 3, 1, "COMPACT"));
  }

  private static EventBridgeProperties propertiesWith(
      final List<TopicProperties> topics,
      final Integer brokerPartitionCount,
      final Integer raftReplicationFactor) {
    final var broker =
        brokerPartitionCount == null
            ? null
            : new BrokerProperties(brokerPartitionCount, null, null, null);
    final var raft =
        raftReplicationFactor == null ? null : new RaftProperties(raftReplicationFactor);
    return new EventBridgeProperties(
        null, broker, null, null, null, null, null, raft, null, topics, null);
  }
}
