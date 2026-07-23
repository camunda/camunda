/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.objects;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.lake.serving.objects.ObjectsStatsService.ObjectsStatsQuery;
import io.camunda.analytics.lake.serving.objects.ObjectsStatsService.ObjectsStatsResult;
import io.camunda.analytics.lake.serving.support.ParquetFixtures;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * A warehouse with only the {@code objects} view (no {@code object_relations}, no {@code
 * object_lifecycle} at all) -- proving {@link ObjectsStatsService#stats} degrades {@code
 * relationFanout} and {@code outcomes} to empty lists independently, one view genuinely absent from
 * the warehouse (not merely absent rows for the queried type), while {@code byProcess} still works
 * off the one view that does exist. Same graceful-degradation contract {@link
 * ObjectsLifecycleTest}/{@link ObjectsServiceTest} already prove for {@link ObjectsService}.
 */
@SpringBootTest
class ObjectsStatsGracefulDegradationTest {

  private static final String OBJECT_TYPE = "invoice";
  private static final String PROCESS_ID = "orderProcess";

  @TempDir private static Path warehouseDir;

  @Autowired private ObjectsStatsService objectsStatsService;

  @DynamicPropertySource
  static void lakeServingProperties(final DynamicPropertyRegistry registry) {
    ParquetFixtures.writeTableFromQuery(
        warehouseDir,
        "objects",
        "SELECT '"
            + OBJECT_TYPE
            + "' AS object_type, 'INV-1' AS object_id, 9001 AS instance_key, '"
            + PROCESS_ID
            + "' AS process_id, 1 AS version, CAST(NULL AS BIGINT) AS scope_key, "
            + "'root' AS qualifier, CAST(TIMESTAMP '2024-01-01 00:00:00' AS TIMESTAMPTZ) AS first_seen");
    registry.add("lake.serving.warehouse-dir", () -> warehouseDir.toString());
  }

  @Test
  void shouldDegradeRelationsAndLifecycleToEmptyWhenNeitherViewExists() {
    final ObjectsStatsResult result = objectsStatsService.stats(new ObjectsStatsQuery(OBJECT_TYPE));

    assertThat(result.byProcess())
        .containsExactly(new ObjectsStatsService.ByProcessRow(PROCESS_ID, 1));
    assertThat(result.relationFanout()).isEmpty();
    assertThat(result.outcomes()).isEmpty();
    assertThat(result.sql()).hasSize(1); // only byProcess's query ran
  }
}
