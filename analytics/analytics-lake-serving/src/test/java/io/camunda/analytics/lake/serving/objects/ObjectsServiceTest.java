/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.objects;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.lake.serving.objects.ObjectsService.JourneyActivity;
import io.camunda.analytics.lake.serving.objects.ObjectsService.JourneyQuery;
import io.camunda.analytics.lake.serving.objects.ObjectsService.JourneyResult;
import io.camunda.analytics.lake.serving.objects.ObjectsService.ObjectListQuery;
import io.camunda.analytics.lake.serving.objects.ObjectsService.ObjectListResult;
import io.camunda.analytics.lake.serving.objects.ObjectsService.ObjectRow;
import io.camunda.analytics.lake.serving.objects.ObjectsService.ObjectTypesResult;
import io.camunda.analytics.lake.serving.support.ObjectFabricFixtures;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** {@code /api/objects/*} against {@link ObjectFabricFixtures}' planted scope/root scenario. */
@SpringBootTest
class ObjectsServiceTest {

  @TempDir private static Path warehouseDir;

  @Autowired private ObjectsService objectsService;

  @DynamicPropertySource
  static void lakeServingProperties(final DynamicPropertyRegistry registry) {
    ObjectFabricFixtures.build(warehouseDir);
    registry.add("lake.serving.warehouse-dir", () -> warehouseDir.toString());
  }

  @Test
  void shouldListDistinctObjectTypesAndReportNoLifecycleSupport() {
    final ObjectTypesResult result = objectsService.types();

    assertThat(result.types()).containsExactly(ObjectFabricFixtures.OBJECT_TYPE);
    assertThat(result.closedSupported()).isFalse();
  }

  @Test
  void shouldListObjectsOfATypeAsOpenWhenNoLifecycleViewExists() {
    final ObjectListResult result =
        objectsService.list(
            new ObjectListQuery(ObjectFabricFixtures.OBJECT_TYPE, "OPEN", null, null));

    assertThat(result.rows()).hasSize(2);
    assertThat(result.rows()).allSatisfy(row -> assertThat(row.closedAt()).isNull());
  }

  @Test
  void shouldReturnNoObjectsWhenFilteringToClosedWithoutALifecycleView() {
    final ObjectListResult result =
        objectsService.list(
            new ObjectListQuery(ObjectFabricFixtures.OBJECT_TYPE, "CLOSED", null, null));

    assertThat(result.rows()).isEmpty();
  }

  @Test
  void shouldOrderByFirstSeenDescendingByDefault() {
    final ObjectListResult result =
        objectsService.list(
            new ObjectListQuery(ObjectFabricFixtures.OBJECT_TYPE, "ALL", null, null, null));

    assertThat(result.rows())
        .extracting(ObjectRow::objectId)
        .containsExactly(ObjectFabricFixtures.SCOPE_OBJECT_ID, ObjectFabricFixtures.ROOT_OBJECT_ID);
  }

  @Test
  void shouldFallBackToFirstSeenOrderWhenDurationSortIsRequestedButNoLifecycleViewExists() {
    final ObjectListResult result =
        objectsService.list(
            new ObjectListQuery(
                ObjectFabricFixtures.OBJECT_TYPE, "ALL", null, null, "DURATION_DESC"));

    // duration_ms doesn't exist without object_lifecycle -- the sort request degrades to the
    // default first_seen-descending order instead of erroring.
    assertThat(result.rows())
        .extracting(ObjectRow::objectId)
        .containsExactly(ObjectFabricFixtures.SCOPE_OBJECT_ID, ObjectFabricFixtures.ROOT_OBJECT_ID);
  }

  @Test
  void shouldAttributeAScopedSightingToItsSubtreeOnly() {
    final JourneyResult result =
        objectsService.journey(
            new JourneyQuery(
                ObjectFabricFixtures.OBJECT_TYPE, ObjectFabricFixtures.SCOPE_OBJECT_ID));

    assertThat(result.sightings()).hasSize(1);
    assertThat(result.sightings().get(0).scopeKey()).isEqualTo(200L);

    final List<String> elementIds =
        result.activities().stream().map(JourneyActivity::elementId).toList();
    assertThat(elementIds).containsExactlyInAnyOrder("B", "C");
    assertThat(result.activities())
        .allSatisfy(a -> assertThat(a.attributedVia()).isEqualTo("SCOPE"));
  }

  @Test
  void shouldAttributeARootSightingToTheWholeInstance() {
    final JourneyResult result =
        objectsService.journey(
            new JourneyQuery(
                ObjectFabricFixtures.OBJECT_TYPE, ObjectFabricFixtures.ROOT_OBJECT_ID));

    final List<String> elementIds =
        result.activities().stream().map(JourneyActivity::elementId).toList();
    assertThat(elementIds).containsExactlyInAnyOrder("A", "B", "C");
    assertThat(result.activities())
        .allSatisfy(a -> assertThat(a.attributedVia()).isEqualTo("ROOT"));

    assertThat(result.links()).hasSize(1);
    assertThat(result.links().get(0).childInstanceKey())
        .isEqualTo(ObjectFabricFixtures.CHILD_INSTANCE_KEY);

    assertThat(result.relations()).hasSize(1);
    assertThat(result.relations().get(0).childId()).isEqualTo("LI-1");
  }

  @Test
  void shouldTimeOrderJourneyActivities() {
    final JourneyResult result =
        objectsService.journey(
            new JourneyQuery(
                ObjectFabricFixtures.OBJECT_TYPE, ObjectFabricFixtures.ROOT_OBJECT_ID));

    final List<String> order =
        result.activities().stream().map(JourneyActivity::elementId).toList();
    assertThat(order).containsExactly("A", "B", "C");
  }
}
