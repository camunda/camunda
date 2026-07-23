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
import io.camunda.analytics.lake.serving.objects.ObjectsService.ObjectRow;
import io.camunda.analytics.lake.serving.objects.ObjectsStatsService.ObjectsStatsQuery;
import io.camunda.analytics.lake.serving.objects.ObjectsStatsService.ObjectsStatsResult;
import io.camunda.analytics.lake.serving.support.ObjectFabricFixtures;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * {@code POST /api/objects/stats} against {@link ObjectFabricFixtures#buildStatsScenario} (four
 * {@code order} objects across two processes, a fanout distribution, and three lifecycle rows --
 * see that method's ground-truth table). Also covers the new {@code sort=DURATION_DESC} ordering on
 * {@link ObjectsService#list} against the same scenario, since {@link #ORD_4}'s open (no lifecycle
 * row) status is exactly what proves {@code NULLS LAST}.
 */
@SpringBootTest
class ObjectsStatsServiceTest {

  @TempDir private static Path warehouseDir;

  @Autowired private ObjectsStatsService objectsStatsService;
  @Autowired private ObjectsService objectsService;

  @DynamicPropertySource
  static void lakeServingProperties(final DynamicPropertyRegistry registry) {
    ObjectFabricFixtures.buildStatsScenario(warehouseDir);
    registry.add("lake.serving.warehouse-dir", () -> warehouseDir.toString());
  }

  @Test
  void shouldCountDistinctObjectsSightedByEachProcess() {
    final ObjectsStatsResult result =
        objectsStatsService.stats(new ObjectsStatsQuery(ObjectFabricFixtures.STATS_OBJECT_TYPE));

    assertThat(result.byProcess()).hasSize(2);
    assertThat(result.byProcess().get(0).processId())
        .isEqualTo(ObjectFabricFixtures.STATS_PROCESS_A);
    assertThat(result.byProcess().get(0).n()).isEqualTo(3); // ORD-1 (x2 sightings), ORD-2, ORD-3
    assertThat(result.byProcess().get(1).processId())
        .isEqualTo(ObjectFabricFixtures.STATS_PROCESS_B);
    assertThat(result.byProcess().get(1).n()).isEqualTo(1); // ORD-4
  }

  @Test
  void shouldReportRelationFanoutDistributionOrderedByChildCountAscending() {
    final ObjectsStatsResult result =
        objectsStatsService.stats(new ObjectsStatsQuery(ObjectFabricFixtures.STATS_OBJECT_TYPE));

    assertThat(result.relationFanout()).hasSize(3);
    assertThat(result.relationFanout().get(0).children()).isEqualTo(1);
    assertThat(result.relationFanout().get(0).n()).isEqualTo(1); // ORD-2
    assertThat(result.relationFanout().get(1).children()).isEqualTo(2);
    assertThat(result.relationFanout().get(1).n()).isEqualTo(1); // ORD-3
    assertThat(result.relationFanout().get(2).children()).isEqualTo(3);
    assertThat(result.relationFanout().get(2).n()).isEqualTo(2); // ORD-1, ORD-4
  }

  @Test
  void shouldReportOutcomeCountsOrderedByCountDescending() {
    final ObjectsStatsResult result =
        objectsStatsService.stats(new ObjectsStatsQuery(ObjectFabricFixtures.STATS_OBJECT_TYPE));

    assertThat(result.outcomes()).hasSize(2);
    assertThat(result.outcomes().get(0).outcome()).isEqualTo("COMPLETED");
    assertThat(result.outcomes().get(0).n()).isEqualTo(2); // ORD-1, ORD-2
    assertThat(result.outcomes().get(1).outcome()).isEqualTo("CANCELLED");
    assertThat(result.outcomes().get(1).n()).isEqualTo(1); // ORD-3
  }

  @Test
  void shouldEchoOneSqlStatementPerNonEmptyList() {
    final ObjectsStatsResult result =
        objectsStatsService.stats(new ObjectsStatsQuery(ObjectFabricFixtures.STATS_OBJECT_TYPE));

    assertThat(result.sql()).hasSize(3);
  }

  @Test
  void shouldOrderObjectsByDurationDescendingWithOpenObjectsLast() {
    final ObjectListResult result =
        objectsService.list(
            new ObjectListQuery(
                ObjectFabricFixtures.STATS_OBJECT_TYPE, "ALL", null, null, "DURATION_DESC"));

    final List<String> order = result.rows().stream().map(ObjectRow::objectId).toList();
    assertThat(order)
        .containsExactly(
            ObjectFabricFixtures.ORD_2, // 900_000 ms
            ObjectFabricFixtures.ORD_1, // 500_000 ms
            ObjectFabricFixtures.ORD_3, // 200_000 ms
            ObjectFabricFixtures.ORD_4); // open -- NULLS LAST
    assertThat(result.rows().get(3).durationMs()).isNull();
  }
}
