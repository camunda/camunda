/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.stream.job.JobActivationPropertiesImpl;
import io.camunda.zeebe.protocol.record.value.TenantFilter;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

final class JobStreamServiceStepTest {

  @Nested
  final class StartupBehavior {
    private static final TestConcurrencyControl CONCURRENCY_CONTROL = new TestConcurrencyControl();

    private final List<CompletableActorFuture<Void>> scheduledActorFutures = new ArrayList<>();
    private MockBrokerStartupContext ctx;
    private ActorFuture<BrokerStartupContext> startupFuture;
    private final JobStreamServiceStep sut = new JobStreamServiceStep();

    @BeforeEach
    void setUp() {
      scheduledActorFutures.clear();
      startupFuture = CONCURRENCY_CONTROL.createFuture();
      ctx = new MockBrokerStartupContext();
      ctx.setConcurrencyControl(CONCURRENCY_CONTROL);
      // Return incomplete futures so ActorFutureCollector's index loop finishes registering all
      // callbacks before any callback fires and modifies the pending-futures list.
      final var mockScheduler = mock(ActorSchedulingService.class);
      when(mockScheduler.submitActor(any()))
          .thenAnswer(
              inv -> {
                final var f = new CompletableActorFuture<Void>();
                scheduledActorFutures.add(f);
                return f;
              });
      ctx.setActorSchedulingService(mockScheduler);
    }

    /** Completes all scheduled actor futures, including ones added during completion callbacks. */
    private void completeAllScheduledActors() {
      for (int i = 0; i < scheduledActorFutures.size(); i++) {
        scheduledActorFutures.get(i).complete(null);
      }
    }

    @Test
    void shouldStoreJobStreamServiceInEngineContextAfterStartup() {
      // given — PhysicalTenantIds.DEFAULT has only DEFAULT_PHYSICAL_TENANT_ID as known tenant

      // when
      sut.startupInternal(ctx, CONCURRENCY_CONTROL, startupFuture);
      completeAllScheduledActors();

      // then
      assertThat(startupFuture.isDone()).as("startup completed").isTrue();
      assertThat(ctx.getPhysicalTenantEngineContext(DEFAULT_PHYSICAL_TENANT_ID).jobStreamService())
          .isNotNull();
    }

    @Test
    void shouldCreateDistinctServicesForEachPhysicalTenant() {
      // given — two distinct physical tenants
      final var secondTenantId = "second";
      ctx.setPhysicalTenantIds(() -> Set.of(DEFAULT_PHYSICAL_TENANT_ID, secondTenantId));

      // when
      sut.startupInternal(ctx, CONCURRENCY_CONTROL, startupFuture);
      completeAllScheduledActors();

      // then — each tenant gets its own isolated service instance
      assertThat(startupFuture.isDone()).as("startup completed").isTrue();
      final var defaultService =
          ctx.getPhysicalTenantEngineContext(DEFAULT_PHYSICAL_TENANT_ID).jobStreamService();
      final var secondService =
          ctx.getPhysicalTenantEngineContext(secondTenantId).jobStreamService();
      assertThat(defaultService).isNotNull();
      assertThat(secondService).isNotNull();
      assertThat(defaultService).isNotSameAs(secondService);
    }

    @Test
    void shouldNotRegisterErrorHandlerAsGlobalPartitionListener() {
      // given
      final var spyCtx = spy(ctx);

      // when
      sut.startupInternal(spyCtx, CONCURRENCY_CONTROL, startupFuture);
      completeAllScheduledActors();

      // then — error handler goes into each PartitionManager's listeners, not the global list
      assertThat(startupFuture.isDone()).as("startup completed").isTrue();
      verify(spyCtx, never()).addPartitionListener(any());
    }
  }

  @Nested
  final class ImmutableJobActivationPropertiesTest {
    @Test
    void shouldDeserializeImmutableActivationProperties() {
      // given
      final var worker = BufferUtil.wrapString("worker");
      final var properties =
          new JobActivationPropertiesImpl()
              .setTimeout(250)
              .setFetchVariables(Set.of(new StringValue("foo"), new StringValue("bar")))
              .setWorker(worker, 0, worker.capacity())
              .setTenantIds(List.of("tenant1", "tenant2"))
              .setTenantFilter(TenantFilter.ASSIGNED);
      final var buffer = BufferUtil.createCopy(properties);

      // when
      final var immutable = JobStreamServiceStep.readJobActivationProperties(buffer);

      // then
      assertThat(immutable.worker()).isEqualTo(worker).isNotSameAs(worker);
      assertThat(immutable.timeout()).isEqualTo(250L);
      assertThat(immutable.fetchVariables())
          .containsExactlyInAnyOrder(BufferUtil.wrapString("foo"), BufferUtil.wrapString("bar"));
      assertThat(immutable.tenantIds()).containsExactlyInAnyOrder("tenant1", "tenant2");
      assertThat(immutable.tenantFilter()).isEqualTo(TenantFilter.ASSIGNED);
    }
  }
}
