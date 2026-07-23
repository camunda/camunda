/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.objects;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.lake.serving.objects.ObjectsService.ObjectListQuery;
import io.camunda.analytics.lake.serving.objects.ObjectsService.ObjectListResult;
import io.camunda.analytics.lake.serving.objects.ObjectsService.ObjectTypesResult;
import io.camunda.analytics.lake.serving.support.ObjectFabricFixtures;
import io.camunda.analytics.lake.serving.support.ParquetFixtures;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Same object-fabric scenario as {@link ObjectsServiceTest}, but with an {@code object_lifecycle}
 * view present -- proving {@code closedSupported} flips to {@code true} and the {@code OPEN}/{@code
 * CLOSED} filters actually discriminate once real lifecycle data exists (only {@code INV-SCOPE} is
 * closed; {@code INV-ROOT} has no lifecycle row, so it stays {@code OPEN}).
 */
@SpringBootTest
class ObjectsLifecycleTest {

  @TempDir private static Path warehouseDir;

  @Autowired private ObjectsService objectsService;

  @DynamicPropertySource
  static void lakeServingProperties(final DynamicPropertyRegistry registry) {
    ObjectFabricFixtures.build(warehouseDir);
    ParquetFixtures.writeTableFromQuery(
        warehouseDir,
        "object_lifecycle",
        "SELECT '"
            + ObjectFabricFixtures.OBJECT_TYPE
            + "' AS object_type, '"
            + ObjectFabricFixtures.SCOPE_OBJECT_ID
            + "' AS object_id, "
            + "CAST(TIMESTAMP '2024-01-01 00:01:00' AS TIMESTAMPTZ) AS birth_ts, "
            + "CAST(TIMESTAMP '2024-01-01 00:05:00' AS TIMESTAMPTZ) AS closed_at, "
            + "240000 AS duration_ms, 'PAID' AS outcome, 2 AS n_sightings");
    registry.add("lake.serving.warehouse-dir", () -> warehouseDir.toString());
  }

  @Test
  void shouldReportLifecycleSupportOnceTheViewExists() {
    final ObjectTypesResult result = objectsService.types();

    assertThat(result.closedSupported()).isTrue();
  }

  @Test
  void shouldListOnlyTheClosedObject() {
    final ObjectListResult result =
        objectsService.list(
            new ObjectListQuery(ObjectFabricFixtures.OBJECT_TYPE, "CLOSED", null, null));

    assertThat(result.rows()).hasSize(1);
    assertThat(result.rows().get(0).objectId()).isEqualTo(ObjectFabricFixtures.SCOPE_OBJECT_ID);
    assertThat(result.rows().get(0).outcome()).isEqualTo("PAID");
    assertThat(result.rows().get(0).durationMs()).isEqualTo(240000L);
  }

  @Test
  void shouldListOnlyTheOpenObject() {
    final ObjectListResult result =
        objectsService.list(
            new ObjectListQuery(ObjectFabricFixtures.OBJECT_TYPE, "OPEN", null, null));

    assertThat(result.rows()).hasSize(1);
    assertThat(result.rows().get(0).objectId()).isEqualTo(ObjectFabricFixtures.ROOT_OBJECT_ID);
    assertThat(result.rows().get(0).closedAt()).isNull();
  }

  @Test
  void shouldListBothWhenStatusIsAll() {
    final ObjectListResult result =
        objectsService.list(
            new ObjectListQuery(ObjectFabricFixtures.OBJECT_TYPE, "ALL", null, null));

    assertThat(result.rows()).hasSize(2);
  }
}
