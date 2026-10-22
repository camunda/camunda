/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.logstreams.state;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.eventplane.FloorState;
import io.camunda.zeebe.broker.exporter.stream.ExportersState;
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

final class DbPositionSupplierTest {

  private static final UUID HISTORY = UUID.randomUUID();

  private ZeebeDb<ZbColumnFamilies> db;
  private ExportersState exporters;
  private FloorState floors;
  private DbPositionSupplier positions;

  @BeforeEach
  void setUp(@TempDir final Path directory) {
    db = DefaultZeebeDbFactory.defaultFactory().createDb(directory.toFile());
    exporters = new ExportersState(db, db.createContext());
    floors = new FloorState(db, db.createContext());
    positions = new DbPositionSupplier(db, false);
  }

  @AfterEach
  void tearDown() throws Exception {
    db.close();
  }

  @Test
  void shouldNotBoundCompactionWithoutExportersOrFloor() {
    // then
    assertThat(positions.getLowestExportedPosition()).isEqualTo(Long.MAX_VALUE);
    assertThat(positions.getHighestExportedPosition()).isEqualTo(Long.MIN_VALUE);
  }

  @Test
  void shouldTakeTheExportersPositionsWithoutAFloor() {
    // given
    exporters.setPosition("a", 10);
    exporters.setPosition("b", 20);

    // then
    assertThat(positions.getLowestExportedPosition()).isEqualTo(10);
    assertThat(positions.getHighestExportedPosition()).isEqualTo(20);
  }

  @Test
  void shouldHoldCompactionWithAHeldFloor() {
    // given
    floors.holdIfAbsent();

    // then
    assertThat(positions.getLowestExportedPosition()).isEqualTo(-1);
  }

  @Test
  void shouldHoldCompactionWithAHeldFloorAndExporters() {
    // given
    exporters.setPosition("a", 10);
    floors.holdIfAbsent();

    // then
    assertThat(positions.getLowestExportedPosition()).isEqualTo(-1);
    assertThat(positions.getHighestExportedPosition()).isEqualTo(10);
  }

  @Test
  void shouldTakeTheFloorWithoutExporters() {
    // given
    floors.store(new UploadFloor(HISTORY, 40, 38));

    // then
    assertThat(positions.getLowestExportedPosition()).isEqualTo(38);
    assertThat(positions.getHighestExportedPosition()).isEqualTo(38);
  }

  @Test
  void shouldTakeTheFloorBelowTheExporters() {
    // given
    exporters.setPosition("a", 50);
    exporters.setPosition("b", 60);
    floors.store(new UploadFloor(HISTORY, 40, 38));

    // then
    assertThat(positions.getLowestExportedPosition()).isEqualTo(38);
    assertThat(positions.getHighestExportedPosition()).isEqualTo(60);
  }

  @Test
  void shouldTakeAnExporterBelowTheFloor() {
    // given
    exporters.setPosition("a", 20);
    exporters.setPosition("b", 60);
    floors.store(new UploadFloor(HISTORY, 70, 68));

    // then
    assertThat(positions.getLowestExportedPosition()).isEqualTo(20);
    assertThat(positions.getHighestExportedPosition()).isEqualTo(68);
  }

  @Test
  void shouldNoLongerBoundCompactionOnceTheFloorIsDeleted() {
    // given
    exporters.setPosition("a", 50);
    floors.holdIfAbsent();

    // when
    floors.delete();

    // then
    assertThat(positions.getLowestExportedPosition()).isEqualTo(50);
  }
}
