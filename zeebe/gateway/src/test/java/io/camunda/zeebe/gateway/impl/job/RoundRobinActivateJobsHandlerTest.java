/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.job;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.camunda.zeebe.broker.client.impl.RoundRobinDispatchStrategy;
import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerActivateJobsRequest;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

final class RoundRobinActivateJobsHandlerTest {

  private static final int MAX_JOB_TYPES = 10;

  @RegisterExtension
  final ControlledActorSchedulerExtension actorScheduler = new ControlledActorSchedulerExtension();

  @Test
  void shouldBoundTrackedJobTypes() {
    // given
    final Cache<String, RoundRobinDispatchStrategy> cache =
        Caffeine.newBuilder().maximumSize(MAX_JOB_TYPES).executor(Runnable::run).build();
    final var handler =
        new RoundRobinActivateJobsHandler<Object>(
            new StubbedBrokerClient(),
            Long.MAX_VALUE,
            r -> null,
            IllegalStateException::new,
            cache);
    actorScheduler.submitActor(Actor.newActor().actorStartedHandler(handler).build());
    actorScheduler.workUntilDone();

    // when
    for (int i = 0; i < MAX_JOB_TYPES * 2; i++) {
      final var brokerRequest = new BrokerActivateJobsRequest("type-" + i);
      brokerRequest.setPartitionGroup(DEFAULT_PHYSICAL_TENANT_ID);
      final var request =
          new InflightActivateJobsRequest<Object>(
              i, brokerRequest, new NoopResponseObserver(), 1000);
      handler.activateJobs(3, request, e -> {}, (a, b) -> {});
    }

    // then
    assertThat(cache.estimatedSize()).isEqualTo(MAX_JOB_TYPES);
  }

  private static final class NoopResponseObserver implements ResponseObserver<Object> {

    @Override
    public void onCompleted() {}

    @Override
    public void onNext(final Object element) {}

    @Override
    public boolean isCancelled() {
      return false;
    }

    @Override
    public void onError(final Throwable throwable) {}
  }
}
