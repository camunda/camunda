/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.eventplane;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.broker.exporter.stream.SimplePartitionMessageService;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.state.DefaultZeebeDbFactory;
import io.camunda.zeebe.eventplane.publisher.UploadFloor;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A leader's floor store and a follower's receiver over one messaging service, which delivers what
 * the leader broadcasts to the follower's subscription.
 */
final class FloorDistributionTest {

  private static final PartitionId PARTITION = new PartitionId("default", 3);
  private static final UUID HISTORY = UUID.randomUUID();

  private final SimplePartitionMessageService messaging = new SimplePartitionMessageService();
  private ZeebeDb<ZbColumnFamilies> leaderDb;
  private ZeebeDb<ZbColumnFamilies> followerDb;
  private ReplicatedFloorStore leader;
  private FloorState follower;
  private FloorReceiver receiver;

  @BeforeEach
  void setUp(@TempDir final Path directory) {
    leaderDb = DefaultZeebeDbFactory.defaultFactory().createDb(directory.resolve("l").toFile());
    followerDb = DefaultZeebeDbFactory.defaultFactory().createDb(directory.resolve("f").toFile());
    final var distribution = new FloorDistribution(messaging, PARTITION);
    leader =
        new ReplicatedFloorStore(new FloorState(leaderDb, leaderDb.createContext()), distribution);
    follower = new FloorState(followerDb, followerDb.createContext());
    follower.holdIfAbsent();
    receiver = new FloorReceiver(follower, distribution);
    receiver.open(Runnable::run);
  }

  @AfterEach
  void tearDown() throws Exception {
    receiver.close();
    leaderDb.close();
    followerDb.close();
  }

  @Test
  void shouldSendOnTheFloorsSubject() {
    // then
    assertThat(messaging.consumers).containsOnlyKeys("event-plane-floor-default-3");
  }

  @Test
  void shouldStoreTheLeadersFloorOnTheFollower() {
    // given
    final var floor = new UploadFloor(HISTORY, 40, 38);

    // when
    leader.store(floor);

    // then
    assertThat(follower.floor()).isEqualTo(floor);
    assertThat(follower.position()).hasValue(38);
  }

  @Test
  void shouldIgnoreAFloorGoingBack() {
    // given
    final var floor = new UploadFloor(HISTORY, 40, 38);
    leader.store(floor);

    // when: an old leader's floor, behind the one the follower has
    new FloorDistribution(messaging, PARTITION).send(new UploadFloor(HISTORY, 20, 18));

    // then
    assertThat(follower.floor()).isEqualTo(floor);
  }

  @Test
  void shouldResendTheLatestFloor() {
    // given
    final var floor = new UploadFloor(HISTORY, 40, 38);
    leader.store(floor);
    follower.delete();

    // when
    leader.resend();

    // then
    assertThat(follower.floor()).isEqualTo(floor);
  }

  @Test
  void shouldResendTheLoadedFloor() throws Exception {
    // given: a floor the leader had before, which the follower doesn't
    final var floor = new UploadFloor(HISTORY, 40, 38);
    new FloorState(leaderDb, leaderDb.createContext()).store(floor);
    leader.load();

    // when
    leader.resend();

    // then
    assertThat(follower.floor()).isEqualTo(floor);
  }

  @Test
  void shouldSendNothingWithoutAFloor() {
    // when
    leader.resend();

    // then
    assertThat(follower.position()).hasValue(FloorState.HELD);
  }

  @Test
  void shouldStoreNothingOnceClosed() {
    // given
    receiver.close();

    // when
    leader.store(new UploadFloor(HISTORY, 40, 38));

    // then
    assertThat(messaging.consumers).isEmpty();
    assertThat(follower.position()).hasValue(FloorState.HELD);
  }

  @Test
  void shouldIgnoreAMalformedMessage() {
    // when
    messaging.broadcast("event-plane-floor-default-3", ByteBuffer.allocate(3));

    // then
    assertThat(follower.position()).hasValue(FloorState.HELD);
  }

  @Test
  void shouldRoundTripAFloor() {
    // given
    final var floor = new UploadFloor(HISTORY, 3, -1);

    // when
    final var decoded = FloorDistribution.decode(FloorDistribution.encode(floor));

    // then
    assertThat(decoded).isEqualTo(floor);
  }

  @Test
  void shouldRejectAMessageOfAnotherLength() {
    // when / then
    assertThatThrownBy(() -> FloorDistribution.decode(ByteBuffer.allocate(33)))
        .isInstanceOf(MalformedFloorMessageException.class)
        .hasMessage("Expected an upload floor message of 32 bytes, but got 33 bytes");
  }
}
