/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.broker.client.impl.RoundRobinDispatchStrategy;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerActivateJobsRequest;
import io.camunda.zeebe.scheduler.ActorControl;
import org.junit.jupiter.api.Test;

final class RoundRobinActivateJobsHandlerTest {

  @Test
  @SuppressWarnings("unchecked")
  void shouldBoundTrackedJobTypes() {
    // given
    final var brokerClient = mock(BrokerClient.class);
    when(brokerClient.getTopologyManager()).thenReturn(mock(BrokerTopologyManager.class));
    final Cache<String, RoundRobinDispatchStrategy> cache =
        Caffeine.newBuilder()
            .maximumSize(RoundRobinActivateJobsHandler.MAX_TRACKED_JOB_TYPES)
            .executor(Runnable::run)
            .build();
    final var handler =
        new RoundRobinActivateJobsHandler<Object>(
            brokerClient, Long.MAX_VALUE, r -> null, IllegalStateException::new, cache);
    handler.accept(mock(ActorControl.class));

    // when
    for (int i = 0; i < RoundRobinActivateJobsHandler.MAX_TRACKED_JOB_TYPES * 2; i++) {
      final var request =
          new InflightActivateJobsRequest<Object>(
              i, new BrokerActivateJobsRequest("type-" + i), mock(ResponseObserver.class), 1000);
      handler.activateJobs(3, request, e -> {}, (a, b) -> {});
    }

    // then
    assertThat(cache.estimatedSize())
        .isEqualTo((long) RoundRobinActivateJobsHandler.MAX_TRACKED_JOB_TYPES);
  }
}
