/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.objects;

import io.camunda.analytics.lake.serving.duckdb.LakeQueryService;
import io.camunda.analytics.lake.serving.duckdb.LakeQueryService.QueryResult;
import io.camunda.analytics.lake.serving.duckdb.LakeViewRegistry;
import io.camunda.analytics.lake.serving.sql.SqlText;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Backs the {@code GET /api/objects/type-map} and {@code POST /api/objects/graph} endpoints: a
 * type-level relation summary and a per-object ego graph, both over the same {@code
 * object_relations} / {@code objects} dictionary views {@link ObjectsService} reads.
 *
 * <h2>Ego graph shape</h2>
 *
 * <p>{@code graph(...)} expands two distinct kinds of edges around one ego object:
 *
 * <ul>
 *   <li><b>CONTAINS</b> — {@code object_relations} rows touching the current frontier (either as
 *       parent or child), expanded breadth-first for {@code depth} levels (1 or 2).
 *   <li><b>CO_SIGHTED</b> — pairs of distinct objects sighted by the same {@code instance_key} in
 *       {@code objects}, computed <em>only</em> for the ego object itself (never for the wider
 *       frontier) so the cost stays bounded regardless of {@code depth}.
 * </ul>
 *
 * <p>The result is always capped at {@link #NODE_CAP} nodes (ego included). A node that would push
 * the graph past the cap is dropped along with the one edge that would have introduced it, and
 * {@code truncated} is set — the response is never unbounded.
 *
 * <h2>Open/closed (read-time degradation)</h2>
 *
 * <p>Same rule as {@link ObjectsService}: a missing view is never an error. {@code type-map} reads
 * an empty edge list when {@code object_relations} doesn't exist yet; {@code graph} simply skips
 * whichever half (CONTAINS or CO_SIGHTED) its backing view is missing, still returning the ego node
 * on its own.
 */
@Service
public class ObjectsGraphService {

  private static final String OBJECTS = "objects";
  private static final String OBJECT_RELATIONS = "object_relations";

  /** Hard cap on the number of nodes any {@code graph(...)} response can carry, ego included. */
  private static final int NODE_CAP = 100;

  private final LakeViewRegistry viewRegistry;
  private final LakeQueryService queryService;

  public ObjectsGraphService(
      final LakeViewRegistry viewRegistry, final LakeQueryService queryService) {
    this.viewRegistry = viewRegistry;
    this.queryService = queryService;
  }

  public TypeMapResult typeMap() {
    final boolean available = viewRegistry.ensureAvailable(OBJECT_RELATIONS);
    if (!available) {
      return new TypeMapResult(List.of(), List.of());
    }
    final String sql =
        "SELECT parent_type, child_type, count(*) AS n FROM "
            + SqlText.identifier(OBJECT_RELATIONS)
            + " GROUP BY 1, 2";
    try {
      final QueryResult result = queryService.execute(sql);
      final List<TypeMapEdge> edges = new ArrayList<>(result.rows().size());
      for (final List<Object> row : result.rows()) {
        edges.add(
            new TypeMapEdge(
                (String) row.get(0), (String) row.get(1), ((Number) row.get(2)).longValue()));
      }
      return new TypeMapResult(edges, List.of(sql));
    } catch (final SQLException e) {
      throw new IllegalStateException("Type-map query failed: " + e.getMessage(), e);
    }
  }

  public GraphResult graph(final GraphQuery query) {
    if (query.type() == null
        || query.type().isBlank()
        || query.id() == null
        || query.id().isBlank()) {
      throw new IllegalArgumentException("type and id are required");
    }
    final int depth = clampDepth(query.depth());
    final NodeKey ego = new NodeKey(query.type(), query.id());

    final List<String> sql = new ArrayList<>();
    final LinkedHashSet<NodeKey> visited = new LinkedHashSet<>();
    visited.add(ego);
    final List<GraphEdge> edges = new ArrayList<>();
    final Set<String> seenEdges = new LinkedHashSet<>();
    final boolean[] truncated = {false};

    try {
      expandContains(ego, depth, visited, edges, seenEdges, sql, truncated);
      addCoSighted(ego, visited, edges, sql, truncated);
    } catch (final SQLException e) {
      throw new IllegalStateException("Object-graph query failed: " + e.getMessage(), e);
    }

    final List<GraphNode> nodes =
        visited.stream().map(k -> new GraphNode(k.type(), k.id())).toList();
    return new GraphResult(nodes, edges, truncated[0], sql);
  }

  private void expandContains(
      final NodeKey ego,
      final int depth,
      final LinkedHashSet<NodeKey> visited,
      final List<GraphEdge> edges,
      final Set<String> seenEdges,
      final List<String> sql,
      final boolean[] truncated)
      throws SQLException {
    if (!viewRegistry.ensureAvailable(OBJECT_RELATIONS)) {
      return;
    }
    Set<NodeKey> frontier = Set.of(ego);
    for (int level = 0; level < depth && !frontier.isEmpty(); level++) {
      final String relationsSql =
          "SELECT parent_type, parent_id, child_type, child_id FROM "
              + SqlText.identifier(OBJECT_RELATIONS)
              + " WHERE "
              + frontierPredicate(frontier);
      sql.add(relationsSql);
      final QueryResult result = queryService.execute(relationsSql);
      final LinkedHashSet<NodeKey> nextFrontier = new LinkedHashSet<>();
      for (final List<Object> row : result.rows()) {
        final NodeKey parent = new NodeKey((String) row.get(0), (String) row.get(1));
        final NodeKey child = new NodeKey((String) row.get(2), (String) row.get(3));
        final String edgeKey =
            parent.type() + ":" + parent.id() + "->" + child.type() + ":" + child.id();
        if (!seenEdges.add(edgeKey)) {
          continue;
        }
        final NodeKey newNode =
            visited.contains(parent) ? (visited.contains(child) ? null : child) : parent;
        if (newNode != null && !visited.contains(newNode)) {
          if (visited.size() >= NODE_CAP) {
            truncated[0] = true;
            seenEdges.remove(edgeKey);
            continue;
          }
          visited.add(newNode);
          nextFrontier.add(newNode);
        }
        edges.add(
            new GraphEdge(parent.type(), parent.id(), child.type(), child.id(), "CONTAINS", null));
      }
      frontier = nextFrontier;
    }
  }

  private void addCoSighted(
      final NodeKey ego,
      final LinkedHashSet<NodeKey> visited,
      final List<GraphEdge> edges,
      final List<String> sql,
      final boolean[] truncated)
      throws SQLException {
    if (!viewRegistry.ensureAvailable(OBJECTS)) {
      return;
    }
    final String coSightedSql =
        "SELECT o2.object_type, o2.object_id, COUNT(DISTINCT o1.instance_key) AS n FROM "
            + SqlText.identifier(OBJECTS)
            + " o1 JOIN "
            + SqlText.identifier(OBJECTS)
            + " o2 ON o1.instance_key = o2.instance_key WHERE o1.object_type = "
            + SqlText.literal(ego.type())
            + " AND o1.object_id = "
            + SqlText.literal(ego.id())
            + " AND NOT (o2.object_type = "
            + SqlText.literal(ego.type())
            + " AND o2.object_id = "
            + SqlText.literal(ego.id())
            + ") GROUP BY o2.object_type, o2.object_id";
    sql.add(coSightedSql);
    final QueryResult result = queryService.execute(coSightedSql);
    for (final List<Object> row : result.rows()) {
      final NodeKey other = new NodeKey((String) row.get(0), (String) row.get(1));
      final int nInstances = ((Number) row.get(2)).intValue();
      if (!visited.contains(other)) {
        if (visited.size() >= NODE_CAP) {
          truncated[0] = true;
          continue;
        }
        visited.add(other);
      }
      edges.add(
          new GraphEdge(ego.type(), ego.id(), other.type(), other.id(), "CO_SIGHTED", nInstances));
    }
  }

  /** Each frontier member contributes "touches this node as parent or as child", OR'd together. */
  private static String frontierPredicate(final Set<NodeKey> frontier) {
    return frontier.stream()
        .map(
            k ->
                "((parent_type = "
                    + SqlText.literal(k.type())
                    + " AND parent_id = "
                    + SqlText.literal(k.id())
                    + ") OR (child_type = "
                    + SqlText.literal(k.type())
                    + " AND child_id = "
                    + SqlText.literal(k.id())
                    + "))")
        .collect(Collectors.joining(" OR "));
  }

  private static int clampDepth(final Integer requested) {
    final int depth = requested == null ? 1 : requested;
    return Math.max(1, Math.min(2, depth));
  }

  /** Internal (type, id) identity used to dedupe nodes while building a graph response. */
  private record NodeKey(String type, String id) {}

  /** {@code GET /api/objects/type-map} response. */
  public record TypeMapResult(List<TypeMapEdge> edges, List<String> sql) {}

  /** One {@code object_relations} type pair and how many instance-level edges realize it. */
  public record TypeMapEdge(String parentType, String childType, long n) {}

  /** {@code POST /api/objects/graph} request; {@code depth} defaults to 1 and is capped at 2. */
  public record GraphQuery(String type, String id, Integer depth) {}

  /** One node in an ego graph: an object identified by its (type, id) pair. */
  public record GraphNode(String type, String id) {}

  /**
   * One edge in an ego graph. {@code nInstances} is non-null only for {@code kind = CO_SIGHTED}
   * (the number of instances that sighted both endpoints); {@code CONTAINS} edges leave it null.
   */
  public record GraphEdge(
      String sourceType,
      String sourceId,
      String targetType,
      String targetId,
      String kind,
      Integer nInstances) {}

  /** {@code POST /api/objects/graph} response. */
  public record GraphResult(
      List<GraphNode> nodes, List<GraphEdge> edges, boolean truncated, List<String> sql) {}
}
