/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.transport.stream.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.transport.stream.impl.messages.StreamTopics;
import org.junit.jupiter.api.Test;

final class StreamTopicNamespaceTest {

  @Test
  void shouldKeepTheJobStreamTopicsInTheDefaultNamespace() {
    // given
    final var namespace = StreamTopicNamespace.DEFAULT;

    // when - then
    assertThat(namespace.topic(StreamTopics.ADD, "default"))
        .isEqualTo(StreamTopics.ADD.topic("default"));
    assertThat(namespace.pushTopic()).isEqualTo(StreamTopics.PUSH.legacyTopic());
    assertThat(namespace.hasLegacyTopics()).isTrue();
  }

  @Test
  void shouldSeparateTheTopicsOfAnotherNamespace() {
    // given
    final var namespace = StreamTopicNamespace.of("probe");

    // when - then
    assertThat(namespace.topic(StreamTopics.ADD, "default")).isEqualTo("probe-default-stream-add");
    assertThat(namespace.pushTopic()).isEqualTo("probe-stream-push");
    assertThat(namespace.hasLegacyTopics()).isFalse();
  }
}
