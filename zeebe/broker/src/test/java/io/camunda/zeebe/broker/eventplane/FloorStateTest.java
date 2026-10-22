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

import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.state.DefaultZeebeDbFactory;
import io.camunda.zeebe.eventplane.publisher.UploadFloor;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class FloorStateTest {

  private static final UUID HISTORY = UUID.randomUUID();

  private ZeebeDb<ZbColumnFamilies> db;
  private FloorState floors;

  @BeforeEach
  void setUp(@TempDir final Path directory) {
    db = DefaultZeebeDbFactory.defaultFactory().createDb(directory.toFile());
    floors = new FloorState(db, db.createContext());
  }

  @AfterEach
  void tearDown() throws Exception {
    db.close();
  }

  @Test
  void shouldHaveNoFloorAtFirst() {
    // then
    assertThat(floors.position()).isEmpty();
    assertThat(floors.floor()).isNull();
  }

  @Test
  void shouldHoldWithoutAFloor() {
    // when
    floors.holdIfAbsent();

    // then
    assertThat(floors.position()).hasValue(FloorState.HELD);
    assertThat(floors.floor()).isNull();
  }

  @Test
  void shouldNotHoldAStoredFloor() {
    // given
    final var floor = new UploadFloor(HISTORY, 40, 38);
    floors.store(floor);

    // when
    floors.holdIfAbsent();

    // then
    assertThat(floors.floor()).isEqualTo(floor);
    assertThat(floors.position()).hasValue(38);
  }

  @Test
  void shouldReadTheFloorItStored() {
    // given
    floors.holdIfAbsent();
    final var floor = new UploadFloor(HISTORY, 40, 38);

    // when
    floors.store(floor);

    // then
    assertThat(floors.floor()).isEqualTo(floor);
    assertThat(floors.position()).hasValue(38);
  }

  @Test
  void shouldKeepAFloorWithoutRecords() {
    // given
    floors.holdIfAbsent();
    final var floor = new UploadFloor(HISTORY, 3, -1);

    // when
    floors.store(floor);

    // then
    assertThat(floors.floor()).isEqualTo(floor);
    assertThat(floors.position()).hasValue(-1);
  }

  @Test
  void shouldMoveToAnotherHistory() {
    // given
    floors.store(new UploadFloor(HISTORY, 40, 38));
    final var next = new UploadFloor(UUID.randomUUID(), 45, 41);

    // when
    floors.store(next);

    // then
    assertThat(floors.floor()).isEqualTo(next);
  }

  @Test
  void shouldRejectAPositionGoingBack() {
    // given
    final var floor = new UploadFloor(HISTORY, 40, 38);
    floors.store(floor);

    // when / then
    assertThatThrownBy(() -> floors.store(new UploadFloor(HISTORY, 41, 37)))
        .isInstanceOf(FloorMovedBackException.class)
        .hasMessage("Expected the upload floor to move forward from position 38, but got 37");
    assertThat(floors.floor()).isEqualTo(floor);
  }

  @Test
  void shouldAdvanceOnlyToAFloorAhead() {
    // given
    floors.holdIfAbsent();
    final var floor = new UploadFloor(HISTORY, 40, 38);

    // when
    final boolean first = floors.advance(floor);
    final boolean behind = floors.advance(new UploadFloor(HISTORY, 30, 20));
    final boolean same = floors.advance(floor);

    // then
    assertThat(first).isTrue();
    assertThat(behind).isFalse();
    assertThat(same).isFalse();
    assertThat(floors.floor()).isEqualTo(floor);
  }

  @Test
  void shouldAdvanceToAHigherIndexAtTheSamePosition() {
    // given
    floors.store(new UploadFloor(HISTORY, 40, 38));
    final var floor = new UploadFloor(HISTORY, 42, 38);

    // when
    final boolean advanced = floors.advance(floor);

    // then
    assertThat(advanced).isTrue();
    assertThat(floors.floor()).isEqualTo(floor);
  }

  @Test
  void shouldDeleteTheFloor() {
    // given
    floors.store(new UploadFloor(HISTORY, 40, 38));

    // when
    floors.delete();

    // then
    assertThat(floors.position()).isEmpty();
    assertThat(floors.floor()).isNull();
  }

  @Test
  void shouldKeepTheFloorForAnotherContext() {
    // given
    final var floor = new UploadFloor(HISTORY, 40, 38);
    floors.store(floor);

    // when
    final var other = new FloorState(db, db.createContext());

    // then
    assertThat(other.floor()).isEqualTo(floor);
  }
}
