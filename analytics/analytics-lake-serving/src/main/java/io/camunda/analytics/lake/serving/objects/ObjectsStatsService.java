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
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Backs {@code POST /api/objects/stats}: three independent breakdowns for one object type, each
 * degrading to an empty list (never an error) when its backing view isn't in the warehouse yet --
 * the same open/closed graceful-degradation rule {@link ObjectsService} follows.
 *
 * <ul>
 *   <li><b>byProcess</b> ({@code objects}): how many distinct objects of this type each process's
 *       instances have sighted -- "which processes touch/create these objects".
 *   <li><b>relationFanout</b> ({@code object_relations}): the distribution of child counts across
 *       this type's parent objects -- "how many children per object" (e.g. line items per order).
 *   <li><b>outcomes</b> ({@code object_lifecycle}): closing-outcome counts for this type.
 * </ul>
 */
@Service
public class ObjectsStatsService {

  private static final String OBJECTS = "objects";
  private static final String OBJECT_RELATIONS = "object_relations";
  private static final String OBJECT_LIFECYCLE = "object_lifecycle";
  private static final int TOP_LIMIT = 50;

  private final LakeViewRegistry viewRegistry;
  private final LakeQueryService queryService;

  public ObjectsStatsService(
      final LakeViewRegistry viewRegistry, final LakeQueryService queryService) {
    this.viewRegistry = viewRegistry;
    this.queryService = queryService;
  }

  public ObjectsStatsResult stats(final ObjectsStatsQuery query) {
    final List<String> sql = new ArrayList<>();
    try {
      final List<ByProcessRow> byProcess =
          viewRegistry.ensureAvailable(OBJECTS) ? byProcess(query.type(), sql) : List.of();
      final List<RelationFanoutRow> relationFanout =
          viewRegistry.ensureAvailable(OBJECT_RELATIONS)
              ? relationFanout(query.type(), sql)
              : List.of();
      final List<OutcomeRow> outcomes =
          viewRegistry.ensureAvailable(OBJECT_LIFECYCLE) ? outcomes(query.type(), sql) : List.of();
      return new ObjectsStatsResult(byProcess, relationFanout, outcomes, sql);
    } catch (final SQLException e) {
      throw new IllegalStateException("Object-stats query failed: " + e.getMessage(), e);
    }
  }

  private List<ByProcessRow> byProcess(final String type, final List<String> sql)
      throws SQLException {
    final String s =
        "SELECT process_id, COUNT(DISTINCT object_id) AS n FROM "
            + SqlText.identifier(OBJECTS)
            + " WHERE object_type = "
            + SqlText.literal(type)
            + " GROUP BY process_id ORDER BY n DESC LIMIT "
            + TOP_LIMIT;
    sql.add(s);
    final QueryResult result = queryService.execute(s);
    final List<ByProcessRow> rows = new ArrayList<>(result.rows().size());
    for (final List<Object> row : result.rows()) {
      rows.add(new ByProcessRow((String) row.get(0), ((Number) row.get(1)).longValue()));
    }
    return rows;
  }

  private List<RelationFanoutRow> relationFanout(final String type, final List<String> sql)
      throws SQLException {
    final String s =
        "WITH child_counts AS (SELECT parent_id, COUNT(*) AS children FROM "
            + SqlText.identifier(OBJECT_RELATIONS)
            + " WHERE parent_type = "
            + SqlText.literal(type)
            + " GROUP BY parent_id) SELECT children, COUNT(*) AS n FROM child_counts"
            + " GROUP BY children ORDER BY children ASC LIMIT "
            + TOP_LIMIT;
    sql.add(s);
    final QueryResult result = queryService.execute(s);
    final List<RelationFanoutRow> rows = new ArrayList<>(result.rows().size());
    for (final List<Object> row : result.rows()) {
      rows.add(
          new RelationFanoutRow(
              ((Number) row.get(0)).intValue(), ((Number) row.get(1)).longValue()));
    }
    return rows;
  }

  private List<OutcomeRow> outcomes(final String type, final List<String> sql) throws SQLException {
    final String s =
        "SELECT outcome, COUNT(*) AS n FROM "
            + SqlText.identifier(OBJECT_LIFECYCLE)
            + " WHERE object_type = "
            + SqlText.literal(type)
            + " GROUP BY outcome ORDER BY n DESC LIMIT "
            + TOP_LIMIT;
    sql.add(s);
    final QueryResult result = queryService.execute(s);
    final List<OutcomeRow> rows = new ArrayList<>(result.rows().size());
    for (final List<Object> row : result.rows()) {
      rows.add(new OutcomeRow((String) row.get(0), ((Number) row.get(1)).longValue()));
    }
    return rows;
  }

  /** {@code POST /api/objects/stats} request. */
  public record ObjectsStatsQuery(String type) {}

  /** One process's distinct-object-of-this-type count. */
  public record ByProcessRow(String processId, long n) {}

  /** One point of the children-per-object distribution ({@code children} = k). */
  public record RelationFanoutRow(int children, long n) {}

  /** One closing-outcome's count. */
  public record OutcomeRow(String outcome, long n) {}

  /** {@code POST /api/objects/stats} response. */
  public record ObjectsStatsResult(
      List<ByProcessRow> byProcess,
      List<RelationFanoutRow> relationFanout,
      List<OutcomeRow> outcomes,
      List<String> sql) {}
}
