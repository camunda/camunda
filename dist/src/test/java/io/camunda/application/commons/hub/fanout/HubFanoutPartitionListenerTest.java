/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.hub.fanout;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class HubFanoutPartitionListenerTest {

  private final AtomicInteger created = new AtomicInteger();
  private final HubFanoutPartitionListener listener =
      new HubFanoutPartitionListener(
          () -> {
            created.incrementAndGet();
            // Unreachable Hub: the puller just backs off in its daemon thread.
            return new HubFanoutPuller(
                "default",
                new HubFanoutProperties(
                    true,
                    URI.create("http://localhost:1/"),
                    "oc-1",
                    0,
                    Duration.ofSeconds(1),
                    5,
                    "logging"),
                Map.of(),
                HttpClient.newHttpClient());
          });

  @AfterEach
  void tearDown() {
    listener.stop();
  }

  @Test
  void shouldPullOnlyWhileLeaderOfDeploymentPartition() {
    listener.onBecomingLeader(2, 1, null, null);
    assertThat(listener.isPulling()).isFalse();

    listener.onBecomingLeader(1, 1, null, null);
    assertThat(listener.isPulling()).isTrue();

    listener.onBecomingFollower(1, 2);
    assertThat(listener.isPulling()).isFalse();

    listener.onBecomingLeader(1, 3, null, null);
    listener.onBecomingInactive(1, 3);
    assertThat(listener.isPulling()).isFalse();
  }

  @Test
  void shouldReplacePullerOnNewLeaderTerm() {
    listener.onBecomingLeader(1, 1, null, null);
    listener.onBecomingLeader(1, 2, null, null);

    assertThat(listener.isPulling()).isTrue();
    assertThat(created).hasValue(2);
  }
}
