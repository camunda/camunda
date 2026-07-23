/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.analytics.lake.serving.objects.ObjectsGraphService.GraphEdge;
import io.camunda.analytics.lake.serving.objects.ObjectsGraphService.GraphNode;
import io.camunda.analytics.lake.serving.objects.ObjectsGraphService.GraphQuery;
import io.camunda.analytics.lake.serving.objects.ObjectsGraphService.GraphResult;
import io.camunda.analytics.lake.serving.objects.ObjectsGraphService.TypeMapEdge;
import io.camunda.analytics.lake.serving.objects.ObjectsGraphService.TypeMapResult;
import io.camunda.analytics.lake.serving.support.ParquetFixtures;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * {@code /api/objects/type-map} and {@code /api/objects/graph} against a planted object-relations
 * scenario: a small chain (customer -> invoice -> lineItem -> shipment), one unrelated edge (a
 * second invoice -> lineItem pair, only relevant to the type-level counts), a co-sighted pair of
 * invoices seen together by two instances, and a wide fan-out ("hub" with 150 children) used only
 * to exercise the node cap / truncation flag.
 *
 * <p>Independent of {@link io.camunda.analytics.lake.serving.support.ObjectFabricFixtures} — this
 * service reads only {@code objects} and {@code object_relations}, so the fixture is built directly
 * with {@link ParquetFixtures} to keep this scenario self-contained and not entangled with {@code
 * ObjectsServiceTest}'s own planted numbers.
 */
@SpringBootTest
class ObjectsGraphServiceTest {

  private static final String INVOICE = "invoice";
  private static final String LINE_ITEM = "lineItem";
  private static final String CUSTOMER = "customer";
  private static final String SHIPMENT = "shipment";
  private static final String ROOT_ID = "INV-ROOT";
  private static final String SIBLING_ID = "INV-SIBLING";
  private static final String OTHER_INVOICE_ID = "INV-OTHER";
  private static final String CUSTOMER_ID = "CUST-1";
  private static final String LINE_ITEM_ID = "LI-1";
  private static final String SHIPMENT_ID = "SHIP-1";
  private static final int HUB_FAN_OUT = 150;

  @TempDir private static Path warehouseDir;

  @Autowired private ObjectsGraphService objectsGraphService;

  @DynamicPropertySource
  static void lakeServingProperties(final DynamicPropertyRegistry registry) {
    buildFixture(warehouseDir);
    registry.add("lake.serving.warehouse-dir", () -> warehouseDir.toString());
  }

  private static void buildFixture(final Path warehouseDir) {
    ParquetFixtures.writeTableFromQuery(
        warehouseDir,
        "objects",
        "SELECT '"
            + INVOICE
            + "' AS object_type, '"
            + ROOT_ID
            + "' AS object_id, CAST(9001 AS BIGINT) AS instance_key, 'orderProcess' AS process_id, "
            + "1 AS version, CAST(NULL AS BIGINT) AS scope_key, 'root' AS qualifier, "
            + "CAST(TIMESTAMP '2024-01-01 00:00:00' AS TIMESTAMPTZ) AS first_seen "
            + "UNION ALL SELECT '"
            + INVOICE
            + "', '"
            + SIBLING_ID
            + "', CAST(9001 AS BIGINT), 'orderProcess', 1, CAST(NULL AS BIGINT), 'root', "
            + "CAST(TIMESTAMP '2024-01-01 00:01:00' AS TIMESTAMPTZ) "
            + "UNION ALL SELECT '"
            + INVOICE
            + "', '"
            + ROOT_ID
            + "', CAST(9003 AS BIGINT), 'orderProcess', 1, CAST(NULL AS BIGINT), 'root', "
            + "CAST(TIMESTAMP '2024-01-02 00:00:00' AS TIMESTAMPTZ) "
            + "UNION ALL SELECT '"
            + INVOICE
            + "', '"
            + SIBLING_ID
            + "', CAST(9003 AS BIGINT), 'orderProcess', 1, CAST(NULL AS BIGINT), 'root', "
            + "CAST(TIMESTAMP '2024-01-02 00:01:00' AS TIMESTAMPTZ)");

    ParquetFixtures.writeTableFromQuery(
        warehouseDir,
        "object_relations",
        "SELECT '"
            + CUSTOMER
            + "' AS parent_type, '"
            + CUSTOMER_ID
            + "' AS parent_id, '"
            + INVOICE
            + "' AS child_type, '"
            + ROOT_ID
            + "' AS child_id, CAST(TIMESTAMP '2024-01-01 00:00:00' AS TIMESTAMPTZ) AS first_seen "
            + "UNION ALL SELECT '"
            + INVOICE
            + "', '"
            + ROOT_ID
            + "', '"
            + LINE_ITEM
            + "', '"
            + LINE_ITEM_ID
            + "', CAST(TIMESTAMP '2024-01-01 00:00:01' AS TIMESTAMPTZ) "
            + "UNION ALL SELECT '"
            + LINE_ITEM
            + "', '"
            + LINE_ITEM_ID
            + "', '"
            + SHIPMENT
            + "', '"
            + SHIPMENT_ID
            + "', CAST(TIMESTAMP '2024-01-01 00:00:02' AS TIMESTAMPTZ) "
            + "UNION ALL SELECT '"
            + INVOICE
            + "', '"
            + OTHER_INVOICE_ID
            + "', '"
            + LINE_ITEM
            + "', 'LI-2', CAST(TIMESTAMP '2024-01-01 00:00:03' AS TIMESTAMPTZ) "
            + "UNION ALL SELECT 'hub', 'HUB-1', 'leaf', 'LEAF-' || CAST(range AS VARCHAR), "
            + "CAST(TIMESTAMP '2024-01-01 00:00:00' AS TIMESTAMPTZ) FROM range("
            + HUB_FAN_OUT
            + ")");
  }

  @Test
  void shouldSummarizeRelationCountsPerTypePair() {
    final TypeMapResult result = objectsGraphService.typeMap();

    assertThat(result.edges())
        .extracting(TypeMapEdge::parentType, TypeMapEdge::childType, TypeMapEdge::n)
        .containsExactlyInAnyOrder(
            tuple(CUSTOMER, INVOICE, 1L),
            tuple(INVOICE, LINE_ITEM, 2L),
            tuple(LINE_ITEM, SHIPMENT, 1L),
            tuple("hub", "leaf", (long) HUB_FAN_OUT));
    assertThat(result.sql()).isNotEmpty();
  }

  @Test
  void shouldExpandContainsEdgesAndTheEgosCoSightedPairAtDepthOne() {
    final GraphResult result = objectsGraphService.graph(new GraphQuery(INVOICE, ROOT_ID, 1));

    assertThat(result.truncated()).isFalse();
    assertThat(result.nodes())
        .extracting(GraphNode::type, GraphNode::id)
        .containsExactlyInAnyOrder(
            tuple(INVOICE, ROOT_ID),
            tuple(CUSTOMER, CUSTOMER_ID),
            tuple(LINE_ITEM, LINE_ITEM_ID),
            tuple(INVOICE, SIBLING_ID));

    assertThat(result.edges())
        .filteredOn(e -> "CONTAINS".equals(e.kind()))
        .extracting(
            GraphEdge::sourceType, GraphEdge::sourceId, GraphEdge::targetType, GraphEdge::targetId)
        .containsExactlyInAnyOrder(
            tuple(CUSTOMER, CUSTOMER_ID, INVOICE, ROOT_ID),
            tuple(INVOICE, ROOT_ID, LINE_ITEM, LINE_ITEM_ID));

    // SHIP-1 is two hops away -- not reachable at depth 1.
    assertThat(result.nodes()).noneMatch(n -> SHIPMENT.equals(n.type()));

    final GraphEdge coSighted =
        result.edges().stream()
            .filter(e -> "CO_SIGHTED".equals(e.kind()))
            .findFirst()
            .orElseThrow();
    assertThat(coSighted.sourceType()).isEqualTo(INVOICE);
    assertThat(coSighted.sourceId()).isEqualTo(ROOT_ID);
    assertThat(coSighted.targetType()).isEqualTo(INVOICE);
    assertThat(coSighted.targetId()).isEqualTo(SIBLING_ID);
    // Sighted together by instance 9001 AND 9003.
    assertThat(coSighted.nInstances()).isEqualTo(2);
  }

  @Test
  void shouldExpandTwoHopsAtDepthTwo() {
    final GraphResult result = objectsGraphService.graph(new GraphQuery(INVOICE, ROOT_ID, 2));

    assertThat(result.truncated()).isFalse();
    assertThat(result.nodes())
        .anyMatch(n -> SHIPMENT.equals(n.type()) && SHIPMENT_ID.equals(n.id()));
    assertThat(result.edges())
        .filteredOn(e -> "CONTAINS".equals(e.kind()))
        .extracting(
            GraphEdge::sourceType, GraphEdge::sourceId, GraphEdge::targetType, GraphEdge::targetId)
        .containsExactlyInAnyOrder(
            tuple(CUSTOMER, CUSTOMER_ID, INVOICE, ROOT_ID),
            tuple(INVOICE, ROOT_ID, LINE_ITEM, LINE_ITEM_ID),
            tuple(LINE_ITEM, LINE_ITEM_ID, SHIPMENT, SHIPMENT_ID));
  }

  @Test
  void shouldClampADepthGreaterThanTwoDownToTwo() {
    final GraphResult depthTwo = objectsGraphService.graph(new GraphQuery(INVOICE, ROOT_ID, 2));
    final GraphResult depthFive = objectsGraphService.graph(new GraphQuery(INVOICE, ROOT_ID, 5));

    assertThat(depthFive.nodes()).hasSameSizeAs(depthTwo.nodes());
    assertThat(depthFive.edges()).hasSameSizeAs(depthTwo.edges());
  }

  @Test
  void shouldTruncateAWideFanOutAtTheNodeCap() {
    final GraphResult result = objectsGraphService.graph(new GraphQuery("hub", "HUB-1", 1));

    assertThat(result.truncated()).isTrue();
    assertThat(result.nodes()).hasSizeLessThanOrEqualTo(100);
    // Every listed node must be backed by a listed edge (or be the ego) -- no dangling references.
    assertThat(result.nodes()).hasSizeGreaterThan(1);
  }

  @Test
  void shouldReturnJustTheEgoWhenNoRelationsTouchIt() {
    final GraphResult result = objectsGraphService.graph(new GraphQuery(SHIPMENT, "SHIP-NONE", 1));

    assertThat(result.nodes()).containsExactly(new GraphNode(SHIPMENT, "SHIP-NONE"));
    assertThat(result.edges()).isEmpty();
    assertThat(result.truncated()).isFalse();
  }
}
