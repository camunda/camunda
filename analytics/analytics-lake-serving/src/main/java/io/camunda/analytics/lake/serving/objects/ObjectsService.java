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
import io.camunda.analytics.lake.serving.sql.ViewDescribe;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Backs the {@code GET /api/objects/types}, {@code POST /api/objects/list}, and {@code .../journey}
 * endpoints over the object-fabric dictionary tables ({@code objects}, {@code instance_links},
 * {@code object_relations}) plus the raw {@code activities} table for journey attribution.
 *
 * <h2>Open/closed (v1 read-time rule)</h2>
 *
 * <p>{@code object_lifecycle} may not exist yet in an older warehouse (see this module's own
 * README). When it's absent, every object reads as {@code OPEN} (no lifecycle row means "not known
 * to be closed") and a {@code status = CLOSED} filter returns nothing — never an error.
 *
 * <h2>Journey scope attribution</h2>
 *
 * <p>A sighting with a non-null {@code scope_key} is attributed via a recursive CTE walking {@code
 * activities.flow_scope_key} parent chains within that one instance (every element whose flow-scope
 * chain reaches the sighted scope element, plus the scope element itself) — {@code SCOPE}
 * attribution. Otherwise (root sighting, or an older warehouse whose {@code activities} view lacks
 * {@code flow_scope_key} altogether) every one of the instance's activities is attributed — {@code
 * ROOT}. An instance sighted more than once keeps its first-seen attribution per element (dedup by
 * {@code (instance_key, element_key)}), since a genuine attribution conflict isn't expected.
 */
@Service
public class ObjectsService {

  private static final String OBJECTS = "objects";
  private static final String ACTIVITIES = "activities";
  private static final String INSTANCE_LINKS = "instance_links";
  private static final String OBJECT_RELATIONS = "object_relations";
  private static final String OBJECT_LIFECYCLE = "object_lifecycle";

  private final LakeViewRegistry viewRegistry;
  private final LakeQueryService queryService;

  public ObjectsService(final LakeViewRegistry viewRegistry, final LakeQueryService queryService) {
    this.viewRegistry = viewRegistry;
    this.queryService = queryService;
  }

  public ObjectTypesResult types() {
    viewRegistry.ensureAvailable(OBJECTS);
    final boolean closedSupported = viewRegistry.ensureAvailable(OBJECT_LIFECYCLE);
    final String sql =
        "SELECT DISTINCT object_type FROM " + SqlText.identifier(OBJECTS) + " ORDER BY object_type";
    try {
      final QueryResult result = queryService.execute(sql);
      final List<String> types = new ArrayList<>(result.rows().size());
      for (final List<Object> row : result.rows()) {
        types.add((String) row.get(0));
      }
      return new ObjectTypesResult(types, closedSupported, List.of(sql));
    } catch (final SQLException e) {
      throw new IllegalStateException("Object-types query failed: " + e.getMessage(), e);
    }
  }

  public ObjectListResult list(final ObjectListQuery query) {
    viewRegistry.ensureAvailable(OBJECTS, ACTIVITIES);
    final boolean lifecycleAvailable = viewRegistry.ensureAvailable(OBJECT_LIFECYCLE);
    final String status = query.status() == null ? "ALL" : query.status();
    if ("CLOSED".equals(status) && !lifecycleAvailable) {
      return new ObjectListResult(List.of(), List.of());
    }
    final int limit = query.limit() == null ? 50 : query.limit();
    final int offset = query.offset() == null ? 0 : query.offset();

    final StringBuilder sqlText =
        new StringBuilder(
            "SELECT o.object_id, MIN(o.first_seen) AS first_seen, "
                + "COUNT(DISTINCT o.instance_key) AS n_instances, "
                + "MAX(COALESCE(a.ended_at, a.started_at)) AS last_seen");
    if (lifecycleAvailable) {
      sqlText.append(
          ", MAX(l.closed_at) AS closed_at, MAX(l.outcome) AS outcome, MAX(l.duration_ms) AS duration_ms");
    }
    sqlText
        .append(" FROM ")
        .append(SqlText.identifier(OBJECTS))
        .append(" o LEFT JOIN ")
        .append(SqlText.identifier(ACTIVITIES))
        .append(" a ON a.instance_key = o.instance_key");
    if (lifecycleAvailable) {
      sqlText
          .append(" LEFT JOIN ")
          .append(SqlText.identifier(OBJECT_LIFECYCLE))
          .append(" l ON l.object_type = o.object_type AND l.object_id = o.object_id");
    }
    sqlText.append(" WHERE o.object_type = ").append(SqlText.literal(query.type()));
    sqlText.append(" GROUP BY o.object_id");
    if (lifecycleAvailable && !"ALL".equals(status)) {
      sqlText
          .append(" HAVING MAX(l.closed_at) IS ")
          .append("OPEN".equals(status) ? "NULL" : "NOT NULL");
    }
    sqlText
        .append(" ORDER BY first_seen DESC LIMIT ")
        .append(limit)
        .append(" OFFSET ")
        .append(offset);
    final String sql = sqlText.toString();
    try {
      final QueryResult result = queryService.execute(sql);
      final List<ObjectRow> rows = new ArrayList<>(result.rows().size());
      for (final List<Object> row : result.rows()) {
        rows.add(
            new ObjectRow(
                (String) row.get(0),
                SqlText.toIsoString(row.get(1)),
                ((Number) row.get(2)).longValue(),
                SqlText.toIsoString(row.get(3)),
                lifecycleAvailable ? SqlText.toIsoString(row.get(4)) : null,
                lifecycleAvailable ? (String) row.get(5) : null,
                lifecycleAvailable && row.get(6) != null
                    ? ((Number) row.get(6)).longValue()
                    : null));
      }
      return new ObjectListResult(rows, List.of(sql));
    } catch (final SQLException e) {
      throw new IllegalStateException("Object-list query failed: " + e.getMessage(), e);
    }
  }

  public JourneyResult journey(final JourneyQuery query) {
    viewRegistry.ensureAvailable(OBJECTS, ACTIVITIES, INSTANCE_LINKS, OBJECT_RELATIONS);
    final List<String> sql = new ArrayList<>();
    try {
      final boolean hasFlowScopeKey =
          ViewDescribe.columns(queryService, ACTIVITIES).contains("flow_scope_key");

      final String sightingsSql =
          "SELECT instance_key, process_id, version, scope_key, qualifier, first_seen FROM "
              + SqlText.identifier(OBJECTS)
              + " WHERE object_type = "
              + SqlText.literal(query.type())
              + " AND object_id = "
              + SqlText.literal(query.id())
              + " ORDER BY first_seen";
      sql.add(sightingsSql);
      final QueryResult sightingsResult = queryService.execute(sightingsSql);
      final List<Sighting> sightings = new ArrayList<>(sightingsResult.rows().size());
      final List<Long> instanceKeys = new ArrayList<>();
      for (final List<Object> row : sightingsResult.rows()) {
        final long instanceKey = ((Number) row.get(0)).longValue();
        instanceKeys.add(instanceKey);
        sightings.add(
            new Sighting(
                instanceKey,
                (String) row.get(1),
                ((Number) row.get(2)).intValue(),
                row.get(3) == null ? null : ((Number) row.get(3)).longValue(),
                (String) row.get(4),
                SqlText.toIsoString(row.get(5))));
      }

      final Map<String, JourneyActivity> activitiesByKey = new LinkedHashMap<>();
      for (final Sighting sighting : sightings) {
        final boolean scoped = hasFlowScopeKey && sighting.scopeKey() != null;
        final String activitiesSql =
            scoped ? subtreeSql(sighting) : wholeInstanceSql(sighting.instanceKey());
        sql.add(activitiesSql);
        final QueryResult activitiesResult = queryService.execute(activitiesSql);
        for (final List<Object> row : activitiesResult.rows()) {
          final long elementKey = ((Number) row.get(4)).longValue();
          final String dedupeKey = sighting.instanceKey() + ":" + elementKey;
          activitiesByKey.computeIfAbsent(
              dedupeKey,
              k ->
                  new JourneyActivity(
                      sighting.instanceKey(),
                      (String) row.get(1),
                      (String) row.get(2),
                      SqlText.toIsoString(row.get(6)),
                      SqlText.toIsoString(row.get(7)),
                      row.get(8) == null ? null : ((Number) row.get(8)).longValue(),
                      scoped ? "SCOPE" : "ROOT"));
        }
      }
      final List<JourneyActivity> activities = new ArrayList<>(activitiesByKey.values());
      activities.sort(
          (a, b) -> {
            if (a.startedAt() == null) {
              return b.startedAt() == null ? 0 : 1;
            }
            if (b.startedAt() == null) {
              return -1;
            }
            return a.startedAt().compareTo(b.startedAt());
          });

      final String linksSql = linksSql(instanceKeys);
      sql.add(linksSql);
      final List<Map<String, Object>> links = rowsAsMaps(queryService.execute(linksSql));

      final String relationsSql =
          "SELECT * FROM "
              + SqlText.identifier(OBJECT_RELATIONS)
              + " WHERE (parent_type = "
              + SqlText.literal(query.type())
              + " AND parent_id = "
              + SqlText.literal(query.id())
              + ") OR (child_type = "
              + SqlText.literal(query.type())
              + " AND child_id = "
              + SqlText.literal(query.id())
              + ")";
      sql.add(relationsSql);
      final List<Map<String, Object>> relations = rowsAsMaps(queryService.execute(relationsSql));

      return new JourneyResult(sightings, activities, links, relations, sql);
    } catch (final SQLException e) {
      throw new IllegalStateException("Journey query failed: " + e.getMessage(), e);
    }
  }

  private String subtreeSql(final Sighting sighting) {
    return "WITH RECURSIVE subtree(element_key) AS (SELECT element_key FROM "
        + SqlText.identifier(ACTIVITIES)
        + " WHERE instance_key = "
        + sighting.instanceKey()
        + " AND element_key = "
        + sighting.scopeKey()
        + " UNION ALL SELECT a.element_key FROM "
        + SqlText.identifier(ACTIVITIES)
        + " a JOIN subtree s ON a.flow_scope_key = s.element_key WHERE a.instance_key = "
        + sighting.instanceKey()
        + ") SELECT instance_key, process_id, element_id, element_type, element_key, state, started_at,"
        + " ended_at, duration_ms FROM "
        + SqlText.identifier(ACTIVITIES)
        + " WHERE instance_key = "
        + sighting.instanceKey()
        + " AND element_key IN (SELECT element_key FROM subtree)";
  }

  private String wholeInstanceSql(final long instanceKey) {
    return "SELECT instance_key, process_id, element_id, element_type, element_key, state, started_at,"
        + " ended_at, duration_ms FROM "
        + SqlText.identifier(ACTIVITIES)
        + " WHERE instance_key = "
        + instanceKey;
  }

  private String linksSql(final List<Long> instanceKeys) {
    if (instanceKeys.isEmpty()) {
      return "SELECT * FROM " + SqlText.identifier(INSTANCE_LINKS) + " WHERE FALSE";
    }
    final String inList =
        instanceKeys.stream().map(String::valueOf).reduce((a, b) -> a + ", " + b).orElse("");
    return "SELECT * FROM "
        + SqlText.identifier(INSTANCE_LINKS)
        + " WHERE parent_instance_key IN ("
        + inList
        + ") OR child_instance_key IN ("
        + inList
        + ")";
  }

  private List<Map<String, Object>> rowsAsMaps(final QueryResult result) {
    final List<Map<String, Object>> maps = new ArrayList<>(result.rows().size());
    for (final List<Object> row : result.rows()) {
      final Map<String, Object> map = new LinkedHashMap<>();
      for (int i = 0; i < result.columns().size(); i++) {
        final Object value = row.get(i);
        map.put(
            result.columns().get(i),
            value instanceof OffsetDateTime ? SqlText.toIsoString(value) : value);
      }
      maps.add(map);
    }
    return maps;
  }

  /** One instance's sighting of an object (a row in the {@code objects} dictionary table). */
  public record Sighting(
      long instanceKey,
      String processId,
      int version,
      Long scopeKey,
      String qualifier,
      String firstSeen) {}

  /** One activity attributed to an object's journey. */
  public record JourneyActivity(
      long instanceKey,
      String processId,
      String elementId,
      String startedAt,
      String endedAt,
      Long durationMs,
      String attributedVia) {}

  /** {@code GET /api/objects/types} response. */
  public record ObjectTypesResult(List<String> types, boolean closedSupported, List<String> sql) {}

  /** {@code POST /api/objects/list} request. */
  public record ObjectListQuery(String type, String status, Integer limit, Integer offset) {}

  /** One {@code POST /api/objects/list} result row. */
  public record ObjectRow(
      String objectId,
      String firstSeen,
      long nInstances,
      String lastSeen,
      String closedAt,
      String outcome,
      Long durationMs) {}

  /** {@code POST /api/objects/list} response. */
  public record ObjectListResult(List<ObjectRow> rows, List<String> sql) {}

  /** {@code POST /api/objects/journey} request. */
  public record JourneyQuery(String type, String id) {}

  /** {@code POST /api/objects/journey} response. */
  public record JourneyResult(
      List<Sighting> sightings,
      List<JourneyActivity> activities,
      List<Map<String, Object>> links,
      List<Map<String, Object>> relations,
      List<String> sql) {}
}
