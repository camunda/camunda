/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.eventplane;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.raft.RaftServer.Role;
import io.camunda.zeebe.broker.exporter.stream.SimplePartitionMessageService;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.partitions.PartitionMessagingService;
import io.camunda.zeebe.broker.system.partitions.TestPartitionTransitionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.state.DefaultZeebeDbFactory;
import io.camunda.zeebe.eventplane.publisher.UploadFloor;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The step on followers, and with the Event Plane off; the leader's side runs in the QA tests. */
final class EventPlanePartitionTransitionStepTest {

  private static final String SUBJECT = "event-plane-floor-default-1";

  private final SimplePartitionMessageService messaging = new SimplePartitionMessageService();
  private final BrokerCfg brokerCfg = new BrokerCfg();
  private final EventPlanePartitionTransitionStep step = new EventPlanePartitionTransitionStep();
  private ZeebeDb<ZbColumnFamilies> db;
  private FloorState floors;
  private TestPartitionTransitionContext context;

  @BeforeEach
  void setUp(@TempDir final Path directory) {
    db = DefaultZeebeDbFactory.defaultFactory().createDb(directory.toFile());
    floors = new FloorState(db, db.createContext());
    context =
        new TestPartitionTransitionContext() {
          @Override
          public PartitionMessagingService getMessagingService() {
            return messaging;
          }
        };
    context.setZeebeDb(db);
    context.setBrokerCfg(brokerCfg);
    context.setConcurrencyControl(new TestConcurrencyControl());
  }

  @AfterEach
  void tearDown() throws Exception {
    db.close();
  }

  @Test
  void shouldHoldTheFloorOnAFollower() {
    // given
    brokerCfg.getExperimental().getEventPlane().setEnabled(true);

    // when
    step.transitionTo(context, 1, Role.FOLLOWER).join();

    // then
    assertThat(floors.position()).hasValue(FloorState.HELD);
  }

  @Test
  void shouldStoreTheLeadersFloorOnAFollower() {
    // given
    brokerCfg.getExperimental().getEventPlane().setEnabled(true);
    step.transitionTo(context, 1, Role.FOLLOWER).join();
    final var floor = new UploadFloor(UUID.randomUUID(), 40, 38);

    // when
    new FloorDistribution(messaging, context.partitionId()).send(floor);

    // then
    assertThat(messaging.consumers).containsOnlyKeys(SUBJECT);
    assertThat(floors.floor()).isEqualTo(floor);
  }

  @Test
  void shouldStopReceivingOnTheNextTransition() {
    // given
    brokerCfg.getExperimental().getEventPlane().setEnabled(true);
    step.transitionTo(context, 1, Role.FOLLOWER).join();

    // when
    step.prepareTransition(context, 2, Role.CANDIDATE).join();

    // then
    assertThat(messaging.consumers).isEmpty();
  }

  @Test
  void shouldDeleteTheFloorWhileTheEventPlaneIsOff() {
    // given
    floors.store(new UploadFloor(UUID.randomUUID(), 40, 38));

    // when
    step.transitionTo(context, 1, Role.FOLLOWER).join();

    // then
    assertThat(floors.position()).isEmpty();
    assertThat(messaging.consumers).isEmpty();
  }
}
