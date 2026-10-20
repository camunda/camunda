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
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ReplicatedFloorStoreTest {

  private static final UUID HISTORY = UUID.randomUUID();

  private ZeebeDb<ZbColumnFamilies> db;
  private FloorState state;
  private ReplicatedFloorStore floors;

  @BeforeEach
  void setUp(@TempDir final Path directory) {
    db = DefaultZeebeDbFactory.defaultFactory().createDb(directory.toFile());
    state = new FloorState(db, db.createContext());
    floors =
        new ReplicatedFloorStore(
            new FloorState(db, db.createContext()),
            new FloorDistribution(
                new SimplePartitionMessageService(), new PartitionId("default", 1)));
  }

  @AfterEach
  void tearDown() throws Exception {
    db.close();
  }

  @Test
  void shouldHaveNoFloorBeforeTheFirstUpload() throws Exception {
    // given
    state.holdIfAbsent();

    // when
    final var floor = floors.load();

    // then
    assertThat(floor).isNull();
  }

  @Test
  void shouldLoadTheFloorItStored() throws Exception {
    // given
    final var floor = new UploadFloor(HISTORY, 40, 38);

    // when
    floors.store(floor);

    // then
    assertThat(floors.load()).isEqualTo(floor);
    assertThat(state.position()).hasValue(38);
  }

  @Test
  void shouldKeepAFloorWithoutRecords() throws Exception {
    // given
    final var floor = new UploadFloor(HISTORY, 3, -1);

    // when
    floors.store(floor);

    // then
    assertThat(floors.load()).isEqualTo(floor);
  }

  @Test
  void shouldRejectAPositionGoingBack() {
    // given
    floors.store(new UploadFloor(HISTORY, 40, 38));

    // when / then
    assertThatThrownBy(() -> floors.store(new UploadFloor(HISTORY, 41, 30)))
        .isInstanceOf(FloorMovedBackException.class);
  }
}
