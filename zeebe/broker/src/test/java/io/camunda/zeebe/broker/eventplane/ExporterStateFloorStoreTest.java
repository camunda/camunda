/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.eventplane;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.exporter.stream.ExporterRule;
import io.camunda.zeebe.broker.system.configuration.EventPlaneCfg;
import io.camunda.zeebe.eventplane.publisher.UploadFloor;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class ExporterStateFloorStoreTest {

  @Rule
  public final ExporterRule exporters =
      ExporterRule.activeExporter()
          .withExporterDirectorContextConfigurator(
              c -> c.externalPositionIds(Set.of(EventPlaneCfg.FLOOR_ID)));

  private ExporterStateFloorStore floors;

  @Before
  public void setUp() {
    exporters.startExporterDirector(List.of());
    floors = new ExporterStateFloorStore(exporters.getDirector());
  }

  @After
  public void tearDown() throws Exception {
    exporters.closeExporterDirector();
  }

  @Test
  public void shouldHaveNoFloorBeforeTheFirstUpload() throws Exception {
    // when
    final var floor = floors.load();

    // then
    assertThat(floor).isNull();
  }

  @Test
  public void shouldLoadTheFloorItStored() throws Exception {
    // given
    final var floor = new UploadFloor(UUID.randomUUID(), 40, 38);

    // when
    floors.store(floor);

    // then
    assertThat(floors.load()).isEqualTo(floor);
    assertThat(exporters.getExportersState().getPosition(EventPlaneCfg.FLOOR_ID)).isEqualTo(38);
  }

  @Test
  public void shouldKeepAFloorWithoutRecords() throws Exception {
    // given
    final var floor = new UploadFloor(UUID.randomUUID(), 3, -1);

    // when
    floors.store(floor);

    // then
    assertThat(floors.load()).isEqualTo(floor);
  }
}
