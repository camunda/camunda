/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.ActorScheduler.DefaultActorThreadFactory;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.SchedulingHints;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class PhysicalTenantActorSchedulersTest {

  private final List<ActorScheduler> created = new CopyOnWriteArrayList<>();
  private final List<String> factoryCalls = new CopyOnWriteArrayList<>();
  private ActorScheduler sharedScheduler;

  @BeforeEach
  void setUp() {
    sharedScheduler = newScheduler("shared", "shared-");
    created.clear();
    sharedScheduler.start();
  }

  @AfterEach
  void tearDown() throws Exception {
    sharedScheduler.close();
    for (final var scheduler : new ArrayList<>(created)) {
      try {
        scheduler.close();
      } catch (final Exception ignored) {
        // already stopped
      }
    }
  }

  @Test
  void shouldNotCreateSchedulerBeforeAnActorIsSubmitted() {
    // given
    final var sut = dedicatedSchedulers();

    // when
    sut.schedulingService("tenanta");

    // then
    assertThat(factoryCalls).isEmpty();
  }

  @Test
  void shouldRunActorsOnThreadsOfTheTenant() throws Exception {
    // given
    final var sut = dedicatedSchedulers();
    final var service = sut.schedulingService("tenanta");

    // when
    final var cpuThread = threadNameOf(service, SchedulingHints.cpuBound());
    final var ioThread = threadNameOf(service, SchedulingHints.ioBound());

    // then
    assertThat(cpuThread).isEqualTo("tenanta-zb-actors-0");
    assertThat(ioThread).isEqualTo("tenanta-zb-fs-workers-0");
    assertThat(threadNameOf(sharedScheduler, SchedulingHints.cpuBound()))
        .doesNotStartWith("tenanta");
  }

  @Test
  void shouldRunActorsOfDifferentTenantsOnDifferentSchedulers() throws Exception {
    // given
    final var sut = dedicatedSchedulers();

    // when
    final var threadOfA =
        threadNameOf(sut.schedulingService("tenanta"), SchedulingHints.cpuBound());
    final var threadOfB =
        threadNameOf(sut.schedulingService("tenantb"), SchedulingHints.cpuBound());

    // then
    assertThat(threadOfA).startsWith("tenanta-");
    assertThat(threadOfB).startsWith("tenantb-");
    assertThat(factoryCalls).containsExactlyInAnyOrder("tenanta", "tenantb");
  }

  @Test
  void shouldReuseSchedulerOfTenantForFurtherActors() throws Exception {
    // given
    final var sut = dedicatedSchedulers();
    final var service = sut.schedulingService("tenanta");

    // when
    threadNameOf(service, SchedulingHints.cpuBound());
    threadNameOf(service, SchedulingHints.ioBound());
    threadNameOf(sut.schedulingService("tenanta"), SchedulingHints.cpuBound());

    // then
    assertThat(factoryCalls).containsExactly("tenanta");
  }

  @Test
  void shouldStopSchedulerOfTenantOnRelease() throws Exception {
    // given
    final var sut = dedicatedSchedulers();
    threadNameOf(sut.schedulingService("tenanta"), SchedulingHints.cpuBound());
    final var scheduler = created.get(0);

    // when
    final var released = sut.release("tenanta");

    // then
    assertThat(released).succeedsWithin(Duration.ofSeconds(10));
    assertThatThrownBy(() -> scheduler.submitActor(new Actor() {}))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldCreateNewSchedulerWhenTenantIsUsedAfterRelease() throws Exception {
    // given
    final var sut = dedicatedSchedulers();
    threadNameOf(sut.schedulingService("tenanta"), SchedulingHints.cpuBound());
    assertThat(sut.release("tenanta")).succeedsWithin(Duration.ofSeconds(10));

    // when
    final var thread = threadNameOf(sut.schedulingService("tenanta"), SchedulingHints.cpuBound());

    // then
    assertThat(thread).isEqualTo("tenanta-zb-actors-0");
    assertThat(factoryCalls).containsExactly("tenanta", "tenanta");
  }

  @Test
  void shouldNotAffectOtherTenantsOnRelease() throws Exception {
    // given
    final var sut = dedicatedSchedulers();
    threadNameOf(sut.schedulingService("tenanta"), SchedulingHints.cpuBound());
    threadNameOf(sut.schedulingService("tenantb"), SchedulingHints.cpuBound());

    // when
    assertThat(sut.release("tenanta")).succeedsWithin(Duration.ofSeconds(10));

    // then
    assertThat(threadNameOf(sut.schedulingService("tenantb"), SchedulingHints.cpuBound()))
        .isEqualTo("tenantb-zb-actors-0");
    assertThat(factoryCalls).containsExactly("tenanta", "tenantb");
  }

  @Test
  void shouldCompleteReleaseOfTenantWithoutScheduler() {
    // given
    final var sut = dedicatedSchedulers();

    // when
    final var released = sut.release("unknown");

    // then
    assertThat(released).succeedsWithin(Duration.ofSeconds(1));
  }

  @Test
  void shouldStopAllSchedulersOnClose() throws Exception {
    // given
    final var sut = dedicatedSchedulers();
    threadNameOf(sut.schedulingService("tenanta"), SchedulingHints.cpuBound());
    threadNameOf(sut.schedulingService("tenantb"), SchedulingHints.cpuBound());

    // when
    sut.close();

    // then
    assertThat(created).hasSize(2);
    for (final var scheduler : created) {
      assertThatThrownBy(() -> scheduler.submitActor(new Actor() {}))
          .isInstanceOf(IllegalStateException.class);
    }
  }

  @Test
  void shouldRunActorsOnSharedSchedulerWhenNotDedicated() throws Exception {
    // given
    final var sut = PhysicalTenantActorSchedulers.shared(sharedScheduler);

    // when
    final var thread = threadNameOf(sut.schedulingService("tenanta"), SchedulingHints.cpuBound());

    // then
    assertThat(thread).isEqualTo("shared-zb-actors-0");
    assertThat(sut.schedulingService("tenantb")).isSameAs(sharedScheduler);
  }

  @Test
  void shouldNotStopSharedSchedulerOnReleaseOrClose() throws Exception {
    // given
    final var sut = PhysicalTenantActorSchedulers.shared(sharedScheduler);

    // when
    assertThat(sut.release("tenanta")).succeedsWithin(Duration.ofSeconds(1));
    sut.close();

    // then
    assertThat(threadNameOf(sharedScheduler, SchedulingHints.cpuBound()))
        .isEqualTo("shared-zb-actors-0");
  }

  private PhysicalTenantActorSchedulers dedicatedSchedulers() {
    return PhysicalTenantActorSchedulers.dedicated(
        tenantId -> {
          factoryCalls.add(tenantId);
          return newScheduler(tenantId, tenantId + "-");
        });
  }

  private ActorScheduler newScheduler(final String name, final String threadPrefix) {
    final var scheduler =
        ActorScheduler.newActorScheduler()
            .setSchedulerName(name)
            .setActorThreadFactory(new DefaultActorThreadFactory(threadPrefix))
            .setCpuBoundActorThreadCount(1)
            .setIoBoundActorThreadCount(1)
            .build();
    created.add(scheduler);
    return scheduler;
  }

  private static String threadNameOf(
      final ActorSchedulingService scheduler, final SchedulingHints hints) throws Exception {
    final var name = new CompletableFuture<String>();
    scheduler.submitActor(
        new Actor() {
          @Override
          protected void onActorStarted() {
            name.complete(Thread.currentThread().getName());
          }
        },
        hints);
    return name.get(10, TimeUnit.SECONDS);
  }
}
