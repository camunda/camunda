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
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.broker.jobstream.JobStreamService;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.stream.job.JobActivationPropertiesImpl;
import io.camunda.zeebe.protocol.record.value.TenantFilter;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
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

    @Test
    void shouldRegisterJobStreamServiceInBrokerContextAfterStartup() {
      // when
      sut.startupInternal(ctx, CONCURRENCY_CONTROL, startupFuture);
      completeAllScheduledActors();

      // then
      assertThat(startupFuture.isDone()).as("startup completed").isTrue();
      assertThat(startupFuture.isCompletedExceptionally()).as("no startup error").isFalse();
      assertThat(ctx.getJobStreamService(DEFAULT_PHYSICAL_TENANT_ID)).isNotNull();
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
      assertThat(startupFuture.isCompletedExceptionally()).as("no startup error").isFalse();
      final var defaultService = ctx.getJobStreamService(DEFAULT_PHYSICAL_TENANT_ID);
      final var secondService = ctx.getJobStreamService(secondTenantId);
      assertThat(defaultService).isNotNull();
      assertThat(secondService).isNotNull();
      assertThat(defaultService).isNotSameAs(secondService);
    }

    @Test
    void shouldRegisterAllTenantServicesSupplierWithSpringBrokerBridge() {
      // when
      sut.startupInternal(ctx, CONCURRENCY_CONTROL, startupFuture);
      completeAllScheduledActors();

      // then — the bridge supplier exposes all physical tenant services for the actuator
      assertThat(startupFuture.isDone()).as("startup completed").isTrue();
      assertThat(startupFuture.isCompletedExceptionally()).as("no startup error").isFalse();
      verify(ctx.getSpringBrokerBridge()).registerJobStreamServicesSupplier(notNull());
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
      assertThat(startupFuture.isCompletedExceptionally()).as("no startup error").isFalse();
      verify(spyCtx, never()).addPartitionListener(any());
    }

    @Test
    void shouldShutDownAlreadyStartedServicesWhenOnePhysicalTenantFailsToStart() {
      // given
      final var secondTenantId = "second";
      ctx.setPhysicalTenantIds(
          () -> new LinkedHashSet<>(List.of(DEFAULT_PHYSICAL_TENANT_ID, secondTenantId)));

      final var mockDefaultService = mock(JobStreamService.class);
      // closeAsync must return a pending future so cleanup's CompletionWaiter finishes
      // registering all callbacks before any of them fires and shrinks the pending list.
      when(mockDefaultService.closeAsync(any()))
          .thenAnswer(
              inv -> {
                final var f = new CompletableActorFuture<Void>();
                scheduledActorFutures.add(f);
                return f;
              });

      final var ctxForTest =
          new MockBrokerStartupContext() {
            @Override
            public void addJobStreamService(final String tenantId, final JobStreamService service) {
              super.addJobStreamService(
                  tenantId,
                  DEFAULT_PHYSICAL_TENANT_ID.equals(tenantId) ? mockDefaultService : service);
            }
          };
      ctxForTest.setConcurrencyControl(CONCURRENCY_CONTROL);
      ctxForTest.setPhysicalTenantIds(
          () -> new LinkedHashSet<>(List.of(DEFAULT_PHYSICAL_TENANT_ID, secondTenantId)));

      // The second submitActor call (second's errorHandler) fails; all others stay pending
      // so CompletionWaiter registration loops complete before any callback fires.
      final var actorIndex = new AtomicInteger();
      final var mockSched = mock(ActorSchedulingService.class);
      when(mockSched.submitActor(any()))
          .thenAnswer(
              inv -> {
                final var f = new CompletableActorFuture<Void>();
                scheduledActorFutures.add(f);
                if (actorIndex.getAndIncrement() == 1) {
                  f.completeExceptionally(new RuntimeException("actor start failed"));
                }
                return f;
              });
      ctxForTest.setActorSchedulingService(mockSched);

      // when
      sut.startupInternal(ctxForTest, CONCURRENCY_CONTROL, startupFuture);
      completeAllScheduledActors();

      // then
      assertThat(startupFuture.isDone()).as("startup future resolved").isTrue();
      assertThat(startupFuture.isCompletedExceptionally())
          .as("startup completed exceptionally")
          .isTrue();
      assertThat(ctxForTest.getJobStreamService(DEFAULT_PHYSICAL_TENANT_ID))
          .as("already-started service removed from map on partial failure")
          .isNull();
    }

    private void completeAllScheduledActors() {
      for (int i = 0; i < scheduledActorFutures.size(); i++) {
        final var f = scheduledActorFutures.get(i);
        if (!f.isDone()) {
          f.complete(null);
        }
      }
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
