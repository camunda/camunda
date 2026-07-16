/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.changelog;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.eventbridge.client.EventBridgeClient;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

final class ChangelogTopicsTest {

  private static final String TOPIC = "analytics-stage2-changelog";

  @Test
  void shouldCreateTheChangelogTopicAsCompactWithTheGivenPartitionCount() {
    // given
    final EventBridgeClient client = mock(EventBridgeClient.class);
    when(client.createTopic(TOPIC, 4, 1, "COMPACT"))
        .thenReturn(CompletableFuture.completedFuture(null));

    // when
    ChangelogTopics.ensure(client, TOPIC, 4);

    // then
    verify(client).createTopic(TOPIC, 4, 1, "COMPACT");
  }

  @Test
  void shouldTolerateAnAlreadyExistingTopicIdempotently() {
    // given — the create call fails (e.g. the topic already exists)
    final EventBridgeClient client = mock(EventBridgeClient.class);
    when(client.createTopic(TOPIC, 4, 1, "COMPACT"))
        .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("already exists")));

    // when / then — no exception escapes; re-creating is safe to call repeatedly
    ChangelogTopics.ensure(client, TOPIC, 4);
    ChangelogTopics.ensure(client, TOPIC, 4);
  }
}
