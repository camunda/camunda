/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.actor;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.application.commons.actor.ActorIdleStrategyConfiguration.IdleStrategySupplier;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.zeebe.broker.partitioning.PhysicalTenantActorSchedulers;
import io.camunda.zeebe.broker.system.configuration.TenantActorPoolCfg;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.SchedulingHints;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

final class ActorSchedulerConfigurationTest {

  @Test
  void shouldUseTenantActorPoolThreadCountOverrides() throws Exception {
    // given a root pool with one CPU and IO thread, and a tenant overriding both counts
    final var environment =
        new MockEnvironment()
            .withProperty("camunda.system.actor-pool.enabled", "true")
            .withProperty("camunda.system.actor-pool.cpu-thread-count", "1")
            .withProperty("camunda.system.actor-pool.io-thread-count", "1")
            .withProperty(
                "camunda.physical-tenants.tenanta.system.actor-pool.cpu-thread-count", "2")
            .withProperty("camunda.physical-tenants.tenanta.system.actor-pool.io-thread-count", "3")
            .withProperty(
                "camunda.physical-tenants.tenanta.data.secondary-storage.elasticsearch.index-prefix",
                "tenanta")
            .withProperty(
                "camunda.physical-tenants.tenanta.security.authorizations.enabled", "false");
    UnifiedConfigurationHelper.setCustomEnvironment(environment);

    ActorScheduler sharedScheduler = null;
    PhysicalTenantActorSchedulers tenantSchedulers = null;
    final var releaseActors = new CountDownLatch(1);
    try {
      final var rootConfig = new Camunda();
      Binder.get(environment).bind(Camunda.PREFIX, Bindable.ofInstance(rootConfig));
      final var tenantResolver = PhysicalTenantResolver.of(environment, rootConfig);
      final var rootPool = new TenantActorPoolCfg(true, 1, 1);
      final var schedulerConfiguration =
          new ActorSchedulerConfiguration.SchedulerConfiguration(
              1, 1, 0, false, "Broker", "node", rootPool);
      final var actorSchedulerConfiguration =
          new ActorSchedulerConfiguration(
              schedulerConfiguration,
              IdleStrategySupplier.ofDefault(),
              new ActorClockConfiguration(false),
              new SimpleMeterRegistry());
      sharedScheduler = actorSchedulerConfiguration.scheduler();
      tenantSchedulers =
          actorSchedulerConfiguration.physicalTenantActorSchedulers(
              sharedScheduler, tenantResolver);

      // when CPU- and IO-bound actors occupy the tenant's pool concurrently
      final var cpuThreads = ConcurrentHashMap.<String>newKeySet();
      final var ioThreads = ConcurrentHashMap.<String>newKeySet();
      final var startedActors = new CountDownLatch(5);
      final var tenantScheduler = tenantSchedulers.schedulingService("tenanta");
      for (int i = 0; i < 2; i++) {
        tenantScheduler.submitActor(
            blockingActor(cpuThreads, startedActors, releaseActors), SchedulingHints.cpuBound());
      }
      for (int i = 0; i < 3; i++) {
        tenantScheduler.submitActor(
            blockingActor(ioThreads, startedActors, releaseActors), SchedulingHints.ioBound());
      }

      // then the tenant gets its configured two CPU threads and three IO threads
      assertThat(startedActors.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(cpuThreads).hasSize(2);
      assertThat(ioThreads).hasSize(3);
    } finally {
      releaseActors.countDown();
      if (tenantSchedulers != null) {
        tenantSchedulers.close();
      }
      if (sharedScheduler != null) {
        sharedScheduler.close();
      }
      UnifiedConfigurationHelper.setCustomEnvironment(null);
    }
  }

  @Test
  void shouldUseSharedSchedulerWhenTenantActorPoolIsDisabled() throws Exception {
    // given a broker-wide pool with the tenant pools disabled
    final var environment = new MockEnvironment();
    UnifiedConfigurationHelper.setCustomEnvironment(environment);

    ActorScheduler sharedScheduler = null;
    PhysicalTenantActorSchedulers tenantSchedulers = null;
    try {
      final var rootConfig = new Camunda();
      Binder.get(environment).bind(Camunda.PREFIX, Bindable.ofInstance(rootConfig));
      final var tenantResolver = PhysicalTenantResolver.of(environment, rootConfig);
      final var schedulerConfiguration =
          new ActorSchedulerConfiguration.SchedulerConfiguration(
              1, 1, 0, false, "Broker", "node", TenantActorPoolCfg.DISABLED);
      final var actorSchedulerConfiguration =
          new ActorSchedulerConfiguration(
              schedulerConfiguration,
              IdleStrategySupplier.ofDefault(),
              new ActorClockConfiguration(false),
              new SimpleMeterRegistry());
      sharedScheduler = actorSchedulerConfiguration.scheduler();
      tenantSchedulers =
          actorSchedulerConfiguration.physicalTenantActorSchedulers(
              sharedScheduler, tenantResolver);

      // when an actor of a tenant is submitted
      final var tenantThread = threadNameOf(tenantSchedulers.schedulingService("default"));

      // then it runs on the broker-wide pool instead of a pool named after the tenant
      assertThat(tenantThread).isEqualTo("zb-actors-0");
      assertThat(threadNameOf(sharedScheduler)).isEqualTo(tenantThread);
    } finally {
      if (tenantSchedulers != null) {
        tenantSchedulers.close();
      }
      if (sharedScheduler != null) {
        sharedScheduler.close();
      }
      UnifiedConfigurationHelper.setCustomEnvironment(null);
    }
  }

  @Test
  void shouldCreateTheThreadGroupsOfAllTenantsAsSiblings() throws Exception {
    // given two tenants that override the root pool
    final var environment =
        new MockEnvironment()
            .withProperty("camunda.system.actor-pool.enabled", "true")
            .withProperty("camunda.system.actor-pool.cpu-thread-count", "1")
            .withProperty("camunda.system.actor-pool.io-thread-count", "1")
            .withProperty(
                "camunda.physical-tenants.tenanta.system.actor-pool.cpu-thread-count", "1")
            .withProperty("camunda.physical-tenants.tenanta.system.actor-pool.io-thread-count", "1")
            .withProperty(
                "camunda.physical-tenants.tenanta.data.secondary-storage.elasticsearch.index-prefix",
                "tenanta")
            .withProperty(
                "camunda.physical-tenants.tenanta.security.authorizations.enabled", "false")
            .withProperty(
                "camunda.physical-tenants.tenantb.system.actor-pool.cpu-thread-count", "1")
            .withProperty("camunda.physical-tenants.tenantb.system.actor-pool.io-thread-count", "1")
            .withProperty(
                "camunda.physical-tenants.tenantb.data.secondary-storage.elasticsearch.index-prefix",
                "tenantb")
            .withProperty(
                "camunda.physical-tenants.tenantb.security.authorizations.enabled", "false");
    UnifiedConfigurationHelper.setCustomEnvironment(environment);

    ActorScheduler sharedScheduler = null;
    PhysicalTenantActorSchedulers tenantSchedulers = null;
    try {
      final var rootConfig = new Camunda();
      Binder.get(environment).bind(Camunda.PREFIX, Bindable.ofInstance(rootConfig));
      final var tenantResolver = PhysicalTenantResolver.of(environment, rootConfig);
      final var actorSchedulerConfiguration =
          new ActorSchedulerConfiguration(
              new ActorSchedulerConfiguration.SchedulerConfiguration(
                  1, 1, 0, false, "Broker", "node", new TenantActorPoolCfg(true, 1, 1)),
              IdleStrategySupplier.ofDefault(),
              new ActorClockConfiguration(false),
              new SimpleMeterRegistry());
      sharedScheduler = actorSchedulerConfiguration.scheduler();
      tenantSchedulers =
          actorSchedulerConfiguration.physicalTenantActorSchedulers(
              sharedScheduler, tenantResolver);

      // when each tenant's first actor is submitted from a thread group of its own
      final var first =
          actorThreadGroupOf(tenantSchedulers.schedulingService("tenanta"), "creator-a");
      final var second =
          actorThreadGroupOf(tenantSchedulers.schedulingService("tenantb"), "creator-b");

      // then
      assertThat(first).isNotSameAs(second);
      assertThat(first.getParent()).isSameAs(second.getParent());
    } finally {
      if (tenantSchedulers != null) {
        tenantSchedulers.close();
      }
      if (sharedScheduler != null) {
        sharedScheduler.close();
      }
      UnifiedConfigurationHelper.setCustomEnvironment(null);
    }
  }

  private static String threadNameOf(final ActorSchedulingService schedulingService)
      throws Exception {
    final var threadName = new CompletableFuture<String>();
    schedulingService.submitActor(
        new Actor() {
          @Override
          protected void onActorStarted() {
            threadName.complete(Thread.currentThread().getName());
          }
        },
        SchedulingHints.cpuBound());
    return threadName.get(10, TimeUnit.SECONDS);
  }

  private static ThreadGroup actorThreadGroupOf(
      final ActorSchedulingService scheduler, final String creatorGroupName) throws Exception {
    final var actorGroup = new CompletableFuture<ThreadGroup>();
    final var creator =
        new Thread(
            new ThreadGroup(creatorGroupName),
            () ->
                scheduler.submitActor(
                    new Actor() {
                      @Override
                      protected void onActorStarted() {
                        actorGroup.complete(Thread.currentThread().getThreadGroup());
                      }
                    }));
    creator.start();
    assertThat(creator.join(Duration.ofSeconds(10))).isTrue();
    return actorGroup.get(10, TimeUnit.SECONDS);
  }

  private static Actor blockingActor(
      final Set<String> threadNames,
      final CountDownLatch startedActors,
      final CountDownLatch releaseActors) {
    return new Actor() {
      @Override
      protected void onActorStarted() {
        threadNames.add(Thread.currentThread().getName());
        startedActors.countDown();
        try {
          releaseActors.await();
        } catch (final InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
    };
  }
}
