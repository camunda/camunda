/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.atomix.raft.RaftServer.Role;
import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.broker.eventplane.EventPlaneRegistration;
import io.camunda.zeebe.broker.partitioning.topology.ClusterConfigurationService;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.partitions.TestPartitionTransitionContext;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.SchedulingHints;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import org.junit.jupiter.params.provider.ValueSource;

final class EventPlaneRegistrationStepTest {

  private final List<Actor> submitted = new CopyOnWriteArrayList<>();
  private final BrokerCfg brokerCfg = new BrokerCfg();
  private final EventPlaneRegistrationStep step = new EventPlaneRegistrationStep();
  private ActorScheduler scheduler;

  @BeforeEach
  void start() {
    scheduler = ActorScheduler.newActorScheduler().build();
    scheduler.start();
    // Nothing listens there, and without a cluster id the registration only waits for one.
    brokerCfg.getEventPlane().setEndpoint(URI.create("http://127.0.0.1:1"));
  }

  @AfterEach
  void stop() throws Exception {
    scheduler.close();
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 2, 7})
  void registersWhileLeadingAnyPartition(final int partition) {
    // when
    step.transitionTo(context(partition), 1, Role.LEADER).join();

    // then
    assertThat(submitted).singleElement().isInstanceOf(EventPlaneRegistration.class);
  }

  @ParameterizedTest
  @EnumSource(value = Role.class, names = "LEADER", mode = Mode.EXCLUDE)
  void registersOnlyAsLeader(final Role role) {
    // when
    step.transitionTo(context(1), 1, role).join();

    // then
    assertThat(submitted).isEmpty();
  }

  @Test
  void registersOnlyWithAnEndpoint() {
    // given
    brokerCfg.getEventPlane().setEndpoint(null);

    // when
    step.transitionTo(context(1), 1, Role.LEADER).join();

    // then
    assertThat(submitted).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(Role.class)
  void stopsRegisteringOnTheNextTransition(final Role role) {
    // given
    final var context = context(1);
    step.transitionTo(context, 1, Role.LEADER).join();

    // when
    step.prepareTransition(context, 2, role).join();

    // then
    assertThat(submitted)
        .singleElement()
        .satisfies(actor -> assertThat(actor.isActorClosed()).isTrue());
  }

  private TestPartitionTransitionContext context(final int partition) {
    final var context =
        new TestPartitionTransitionContext() {
          @Override
          public PartitionId partitionId() {
            return new PartitionId("tenanta", partition);
          }
        };
    context.setBrokerCfg(brokerCfg);
    context.setConcurrencyControl(new TestConcurrencyControl());
    context.setActorSchedulingService(new Recording());
    final var configurations = mock(ClusterConfigurationService.class);
    when(configurations.getCurrentClusterConfiguration())
        .thenReturn(CurrentClusterConfiguration.uninitialized());
    context.setClusterConfigurationService(configurations);
    return context;
  }

  /** Runs actors on the scheduler, and remembers them. */
  private final class Recording implements ActorSchedulingService {

    @Override
    public ActorFuture<Void> submitActor(final Actor actor) {
      submitted.add(actor);
      return scheduler.submitActor(actor);
    }

    @Override
    public ActorFuture<Void> submitActor(final Actor actor, final SchedulingHints hints) {
      submitted.add(actor);
      return scheduler.submitActor(actor, hints);
    }
  }
}
