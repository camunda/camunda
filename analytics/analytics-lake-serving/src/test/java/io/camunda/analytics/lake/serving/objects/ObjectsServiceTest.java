/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.objects;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.lake.serving.config.LakeServingProperties;
import io.camunda.analytics.lake.serving.duckdb.LakeQueryService;
import io.camunda.analytics.lake.serving.duckdb.LakeViewRegistry;
import io.camunda.analytics.lake.serving.objects.ObjectsService.JourneyActivity;
import io.camunda.analytics.lake.serving.objects.ObjectsService.JourneyQuery;
import io.camunda.analytics.lake.serving.objects.ObjectsService.JourneyResult;
import io.camunda.analytics.lake.serving.objects.ObjectsService.ObjectListQuery;
import io.camunda.analytics.lake.serving.objects.ObjectsService.ObjectListResult;
import io.camunda.analytics.lake.serving.objects.ObjectsService.ObjectRow;
import io.camunda.analytics.lake.serving.objects.ObjectsService.ObjectTypesResult;
import io.camunda.analytics.lake.serving.objects.ObjectsService.Sighting;
import io.camunda.analytics.lake.serving.support.ObjectFabricFixtures;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.LongStream;
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

  /**
   * Fresh per test method, for the journey lane-cap/attribution tests below: those plant their own
   * throwaway warehouse (see {@link ObjectFabricFixtures#buildJourneyCapScenario} and {@link
   * ObjectFabricFixtures#buildJourneyAttributionEdgeCasesScenario}) rather than appending to the
   * shared {@link #warehouseDir} the rest of this class's tests depend on -- both write to fixed
   * {@code objects}/{@code activities} file paths per warehouse, so sharing one would silently
   * overwrite the other's planted numbers.
   */
  @TempDir private Path scenarioWarehouseDir;

  @Autowired private ObjectsService objectsService;

  @DynamicPropertySource
  static void lakeServingProperties(final DynamicPropertyRegistry registry) {
    ObjectFabricFixtures.build(warehouseDir);
    registry.add("lake.serving.warehouse-dir", () -> warehouseDir.toString());
  }

  /**
   * An {@link ObjectsService} wired directly against {@code warehouseDir} (its own in-memory DuckDB
   * connection, {@link LakeViewRegistry}, {@link LakeQueryService}) rather than the Spring-managed
   * {@link #objectsService} -- so a test can point it at a scenario-specific warehouse without
   * fighting the single shared Spring context/{@code @DynamicPropertySource} this class's other
   * tests rely on.
   */
  private static ObjectsService standaloneJourneyService(final Path warehouseDir)
      throws SQLException {
    final Connection connection = DriverManager.getConnection("jdbc:duckdb:");
    final LakeServingProperties properties =
        new LakeServingProperties(warehouseDir.toString(), null, 500, 15, 5_000_000L, null, 0L);
    final LakeViewRegistry viewRegistry = new LakeViewRegistry(connection, properties);
    viewRegistry.refresh();
    final LakeQueryService queryService = new LakeQueryService(connection, properties);
    return new ObjectsService(viewRegistry, queryService);
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

  @Test
  void shouldReportTotalInstancesAndNotBeTruncatedBelowTheCap() {
    final JourneyResult result =
        objectsService.journey(
            new JourneyQuery(
                ObjectFabricFixtures.OBJECT_TYPE, ObjectFabricFixtures.ROOT_OBJECT_ID));

    assertThat(result.totalInstances()).isEqualTo(1);
    assertThat(result.truncated()).isFalse();
  }

  @Test
  void shouldCapJourneyLanesToTheMostRecentlyActiveInstances() throws SQLException {
    ObjectFabricFixtures.buildJourneyCapScenario(scenarioWarehouseDir);
    final ObjectsService service = standaloneJourneyService(scenarioWarehouseDir);

    final JourneyResult result =
        service.journey(
            new JourneyQuery(
                ObjectFabricFixtures.OBJECT_TYPE, ObjectFabricFixtures.MANY_OBJECT_ID));

    // 25 instances sighted total, only the JOURNEY_INSTANCE_CAP (20) most recent make the cut.
    assertThat(result.totalInstances()).isEqualTo(ObjectFabricFixtures.MANY_INSTANCE_COUNT);
    assertThat(result.truncated()).isTrue();
    assertThat(result.sightings()).hasSize(ObjectsService.JOURNEY_INSTANCE_CAP);
    assertThat(result.activities()).hasSize(ObjectsService.JOURNEY_INSTANCE_CAP);

    // first_seen increases with instance_key here, so "most recent" means "highest key" -- the 5
    // lowest keys (the oldest instances) are the ones dropped.
    final List<Long> keptInstanceKeys =
        result.sightings().stream().map(Sighting::instanceKey).toList();
    final List<Long> expectedKept =
        LongStream.range(
                ObjectFabricFixtures.MANY_FIRST_INSTANCE_KEY + 5,
                ObjectFabricFixtures.MANY_FIRST_INSTANCE_KEY
                    + ObjectFabricFixtures.MANY_INSTANCE_COUNT)
            .boxed()
            .toList();
    assertThat(keptInstanceKeys).containsExactlyInAnyOrderElementsOf(expectedKept);
    final List<Long> droppedInstanceKeys =
        LongStream.range(
                ObjectFabricFixtures.MANY_FIRST_INSTANCE_KEY,
                ObjectFabricFixtures.MANY_FIRST_INSTANCE_KEY + 5)
            .boxed()
            .toList();
    assertThat(keptInstanceKeys).doesNotContainAnyElementsOf(droppedInstanceKeys);
  }

  @Test
  void shouldReturnTheSameCappedInstancesOnRepeatedJourneyQueries() throws SQLException {
    ObjectFabricFixtures.buildJourneyCapScenario(scenarioWarehouseDir);
    final ObjectsService service = standaloneJourneyService(scenarioWarehouseDir);
    final JourneyQuery query =
        new JourneyQuery(ObjectFabricFixtures.OBJECT_TYPE, ObjectFabricFixtures.MANY_OBJECT_ID);

    final List<Long> firstCall =
        service.journey(query).sightings().stream().map(Sighting::instanceKey).toList();
    final List<Long> secondCall =
        service.journey(query).sightings().stream().map(Sighting::instanceKey).toList();

    assertThat(secondCall).isEqualTo(firstCall);
  }

  @Test
  void shouldAttributeRootAndScopeSightingsOfTheSameObjectAcrossInstancesInOneQuery()
      throws SQLException {
    ObjectFabricFixtures.buildJourneyAttributionEdgeCasesScenario(scenarioWarehouseDir);
    final ObjectsService service = standaloneJourneyService(scenarioWarehouseDir);

    final JourneyResult result =
        service.journey(
            new JourneyQuery(
                ObjectFabricFixtures.OBJECT_TYPE, ObjectFabricFixtures.CROSS_OBJECT_ID));

    assertThat(result.sightings()).hasSize(2);
    assertThat(result.totalInstances()).isEqualTo(2);
    assertThat(result.truncated()).isFalse();

    final JourneyActivity rootActivity =
        result.activities().stream()
            .filter(a -> a.instanceKey() == ObjectFabricFixtures.CROSS_ROOT_INSTANCE_KEY)
            .findFirst()
            .orElseThrow();
    assertThat(rootActivity.elementId()).isEqualTo("R");
    assertThat(rootActivity.attributedVia()).isEqualTo("ROOT");

    final List<JourneyActivity> scopeInstanceActivities =
        result.activities().stream()
            .filter(a -> a.instanceKey() == ObjectFabricFixtures.CROSS_SCOPE_INSTANCE_KEY)
            .toList();
    assertThat(scopeInstanceActivities)
        .extracting(JourneyActivity::elementId)
        .containsExactlyInAnyOrder("P", "Q");
    assertThat(scopeInstanceActivities)
        .allSatisfy(a -> assertThat(a.attributedVia()).isEqualTo("SCOPE"));
  }

  @Test
  void shouldAttributeWholeInstanceAsRootWhenTheRootSightingIsEarliest() throws SQLException {
    ObjectFabricFixtures.buildJourneyAttributionEdgeCasesScenario(scenarioWarehouseDir);
    final ObjectsService service = standaloneJourneyService(scenarioWarehouseDir);

    final JourneyResult result =
        service.journey(
            new JourneyQuery(
                ObjectFabricFixtures.OBJECT_TYPE, ObjectFabricFixtures.TIE_ROOT_FIRST_OBJECT_ID));

    assertThat(result.activities())
        .extracting(JourneyActivity::elementId)
        .containsExactlyInAnyOrder("A", "B", "C");
    assertThat(result.activities())
        .allSatisfy(a -> assertThat(a.attributedVia()).isEqualTo("ROOT"));
  }

  @Test
  void shouldAttributeOnlyTheUnclaimedRemainderAsRootWhenTheScopeSightingIsEarliest()
      throws SQLException {
    ObjectFabricFixtures.buildJourneyAttributionEdgeCasesScenario(scenarioWarehouseDir);
    final ObjectsService service = standaloneJourneyService(scenarioWarehouseDir);

    final JourneyResult result =
        service.journey(
            new JourneyQuery(
                ObjectFabricFixtures.OBJECT_TYPE, ObjectFabricFixtures.TIE_SCOPE_FIRST_OBJECT_ID));

    final String rootAttribution =
        result.activities().stream()
            .filter(a -> a.elementId().equals("A"))
            .findFirst()
            .orElseThrow()
            .attributedVia();
    assertThat(rootAttribution).isEqualTo("ROOT");

    final List<JourneyActivity> scopeAttributed =
        result.activities().stream().filter(a -> !a.elementId().equals("A")).toList();
    assertThat(scopeAttributed)
        .extracting(JourneyActivity::elementId)
        .containsExactlyInAnyOrder("B", "C");
    assertThat(scopeAttributed).allSatisfy(a -> assertThat(a.attributedVia()).isEqualTo("SCOPE"));
  }
}
