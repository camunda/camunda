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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
 * ROOT}.
 *
 * <p><b>First-seen-wins, exactly reproduced set-based:</b> an instance can be sighted more than
 * once for the same object -- e.g. once at the root and once at a nested scope. The row-by-row
 * version of this code walked sightings oldest-first and let a {@code computeIfAbsent} on {@code
 * (instance_key, element_key)} keep whichever attribution got there first; because a {@code ROOT}
 * sighting's reach is every element of the instance and a {@code SCOPE} sighting's reach is only
 * its subtree, that reduces to one fact per instance: whichever of "the instance's earliest root
 * sighting" and "the instance's earliest pre-that-root scope sighting(s)" happened first decides
 * the outcome --
 *
 * <ul>
 *   <li>no root sighting ever: every scope sighting's subtree is {@code SCOPE} (elements outside
 *       every subtree are never attributed at all, same as before);
 *   <li>a root sighting exists and is the instance's earliest sighting: the whole instance is
 *       {@code ROOT} (a later scope sighting can't claim anything the root didn't already cover);
 *   <li>a root sighting exists but isn't earliest: scope sightings strictly before it contribute
 *       their subtrees as {@code SCOPE}; the root then claims everything else in the instance as
 *       {@code ROOT} (including any scope subtree that would have arrived <em>after</em> it, which
 *       is why only "before the root" scope sightings are seeded -- a later one can't win a slot
 *       the root already took).
 * </ul>
 *
 * <p>This is what {@link #attributionSql} computes directly (a {@code root_first} sighting time per
 * instance, subtree seeds restricted to {@code first_seen < root_first_seen}, and a {@code NOT
 * EXISTS} anti-join so the {@code ROOT} branch only claims what the {@code SCOPE} branch didn't) --
 * one set-based query standing in for what used to be N per-sighting queries plus a Java dedupe
 * map. A tie on {@code first_seen} between a root and a scope sighting resolves to the root winning
 * (strict {@code <}); the row-by-row version had no defined tie-break either (row order for equal
 * timestamps was whatever the database happened to return), so this is a genuine determinism
 * improvement, not a behavior regression.
 */
@Service
public class ObjectsService {

  /**
   * Journeys render one lane per sighted instance; a convergence object (e.g. a customer sighted by
   * hundreds of instances) would otherwise blow up both the query count and the UI. Only the {@code
   * JOURNEY_INSTANCE_CAP} most recently active instances (by first-seen recency, then {@code
   * instance_key} for a deterministic tie-break) are included -- see {@link
   * JourneyResult#truncated} and {@link JourneyResult#totalInstances}.
   */
  static final int JOURNEY_INSTANCE_CAP = 20;

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
    final boolean sortByDuration = lifecycleAvailable && "DURATION_DESC".equals(query.sort());

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
        .append(" ORDER BY ")
        .append(sortByDuration ? "duration_ms DESC NULLS LAST" : "first_seen DESC")
        .append(" LIMIT ")
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

  /**
   * Query plan (flat, {@code O(1)} statements regardless of how many instances sighted this object
   * -- was 3 flat statements plus one recursive-CTE statement <em>per sighting</em>):
   *
   * <ol>
   *   <li>{@link #sightingsSql}: this object's sightings, already restricted server-side to the
   *       {@value #JOURNEY_INSTANCE_CAP} most-recently-active instances, plus the true (uncapped)
   *       distinct-instance count carried on every row -- one round trip covers both the lane cap
   *       and {@link JourneyResult#totalInstances}.
   *   <li>{@link #attributionSql}: one statement covering every capped instance's activities,
   *       {@code ROOT} and {@code SCOPE} alike -- see the class javadoc for exactly how the two
   *       attribution kinds are computed and reconciled without a per-sighting query.
   *   <li>the {@code instance_links} lookup, restricted to the (bounded, {@code <=
   *       JOURNEY_INSTANCE_CAP}) capped instance set -- an inline literal list is fine at this
   *       size.
   *   <li>the {@code object_relations} lookup (unchanged; already a single flat statement keyed by
   *       type+id, not by instance).
   * </ol>
   */
  public JourneyResult journey(final JourneyQuery query) {
    viewRegistry.ensureAvailable(OBJECTS, ACTIVITIES, INSTANCE_LINKS, OBJECT_RELATIONS);
    final List<String> sql = new ArrayList<>();
    try {
      final boolean hasFlowScopeKey =
          ViewDescribe.columns(queryService, ACTIVITIES).contains("flow_scope_key");

      final String sightingsSql = sightingsSql(query);
      sql.add(sightingsSql);
      final QueryResult sightingsResult = queryService.execute(sightingsSql);
      final List<Sighting> sightings = new ArrayList<>(sightingsResult.rows().size());
      final Set<Long> cappedInstanceKeys = new LinkedHashSet<>();
      int totalInstances = 0;
      for (final List<Object> row : sightingsResult.rows()) {
        final long instanceKey = ((Number) row.get(0)).longValue();
        cappedInstanceKeys.add(instanceKey);
        sightings.add(
            new Sighting(
                instanceKey,
                (String) row.get(1),
                ((Number) row.get(2)).intValue(),
                row.get(3) == null ? null : ((Number) row.get(3)).longValue(),
                (String) row.get(4),
                SqlText.toIsoString(row.get(5))));
        totalInstances = ((Number) row.get(6)).intValue();
      }
      final boolean truncated = totalInstances > JOURNEY_INSTANCE_CAP;

      final String attributionSql = attributionSql(query, cappedInstanceKeys, hasFlowScopeKey);
      sql.add(attributionSql);
      final QueryResult attributionResult = queryService.execute(attributionSql);
      final List<JourneyActivity> activities = new ArrayList<>(attributionResult.rows().size());
      for (final List<Object> row : attributionResult.rows()) {
        activities.add(
            new JourneyActivity(
                ((Number) row.get(0)).longValue(),
                (String) row.get(1),
                (String) row.get(2),
                SqlText.toIsoString(row.get(6)),
                SqlText.toIsoString(row.get(7)),
                row.get(8) == null ? null : ((Number) row.get(8)).longValue(),
                (String) row.get(9)));
      }
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

      final String linksSql = linksSql(cappedInstanceKeys);
      sql.add(linksSql);
      final QueryResult linksResult = queryService.execute(linksSql);
      final List<JourneyLink> links = new ArrayList<>(linksResult.rows().size());
      for (final List<Object> row : linksResult.rows()) {
        links.add(
            new JourneyLink(
                ((Number) row.get(0)).longValue(),
                ((Number) row.get(1)).longValue(),
                (String) row.get(2),
                SqlText.toIsoString(row.get(3))));
      }

      final String relationsSql =
          "SELECT parent_type, parent_id, child_type, child_id, first_seen FROM "
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
      final QueryResult relationsResult = queryService.execute(relationsSql);
      final List<JourneyRelation> relations = new ArrayList<>(relationsResult.rows().size());
      for (final List<Object> row : relationsResult.rows()) {
        relations.add(
            new JourneyRelation(
                (String) row.get(0),
                (String) row.get(1),
                (String) row.get(2),
                (String) row.get(3),
                SqlText.toIsoString(row.get(4))));
      }

      return new JourneyResult(
          sightings, activities, links, relations, totalInstances, truncated, sql);
    } catch (final SQLException e) {
      throw new IllegalStateException("Journey query failed: " + e.getMessage(), e);
    }
  }

  /**
   * This object's sightings, capped server-side to the {@value #JOURNEY_INSTANCE_CAP} most recently
   * active instances (an instance's recency is its latest sighting's {@code first_seen}; ties
   * broken by {@code instance_key} for a deterministic cut) plus the true, uncapped
   * distinct-instance count -- carried on every returned row via a {@code CROSS JOIN} so one round
   * trip answers both "which sightings" and "how many instances, capped or not".
   */
  private String sightingsSql(final JourneyQuery query) {
    final String typeFilter = typeFilter(query, "");
    return "WITH totals AS (SELECT COUNT(DISTINCT instance_key) AS total_instances FROM "
        + SqlText.identifier(OBJECTS)
        + " WHERE "
        + typeFilter
        + "), capped AS (SELECT instance_key FROM (SELECT instance_key, MAX(first_seen) AS"
        + " recency FROM "
        + SqlText.identifier(OBJECTS)
        + " WHERE "
        + typeFilter
        + " GROUP BY instance_key) per_instance ORDER BY recency DESC, instance_key LIMIT "
        + JOURNEY_INSTANCE_CAP
        + ") SELECT o.instance_key, o.process_id, o.version, o.scope_key, o.qualifier,"
        + " o.first_seen, t.total_instances FROM "
        + SqlText.identifier(OBJECTS)
        + " o JOIN capped c ON c.instance_key = o.instance_key CROSS JOIN totals t WHERE "
        + typeFilter(query, "o.")
        + " ORDER BY o.first_seen";
  }

  /**
   * {@code object_type = ? AND object_id = ?}, every column reference prefixed with {@code
   * columnPrefix} (an alias plus a dot, e.g. {@code "o."}, or {@code ""} for an unaliased query) so
   * the same filter text is reusable whether or not the surrounding query aliases {@code objects}.
   */
  private String typeFilter(final JourneyQuery query, final String columnPrefix) {
    return columnPrefix
        + "object_type = "
        + SqlText.literal(query.type())
        + " AND "
        + columnPrefix
        + "object_id = "
        + SqlText.literal(query.id());
  }

  /**
   * One statement attributing every activity across every capped instance at once. With {@code
   * flow_scope_key} available, a single recursive CTE does the whole job: {@code root_first} is
   * each instance's earliest root-sighting time (if any); {@code seeds} is every scope sighting
   * strictly before that time (or every scope sighting, if there's no root sighting at all); {@code
   * subtree} walks {@code flow_scope_key} from those seeds. The final {@code SELECT} unions the
   * subtree elements ({@code SCOPE}) with every other activity of a root-sighted instance that the
   * subtree branch didn't already claim ({@code ROOT}, via {@code NOT EXISTS}) -- see the class
   * javadoc for why this reproduces the row-by-row first-seen-wins dedupe exactly. Without {@code
   * flow_scope_key} (older warehouse), every capped instance is attributed whole, {@code ROOT},
   * same as the legacy per-sighting fallback.
   */
  private String attributionSql(
      final JourneyQuery query, final Set<Long> cappedInstanceKeys, final boolean hasFlowScopeKey) {
    final String activityColumns =
        "instance_key, process_id, element_id, element_type, element_key, state, started_at,"
            + " ended_at, duration_ms";
    if (cappedInstanceKeys.isEmpty()) {
      return "SELECT "
          + activityColumns
          + ", CAST(NULL AS VARCHAR) AS attributed_via FROM "
          + SqlText.identifier(ACTIVITIES)
          + " WHERE FALSE";
    }
    final String inList = instanceKeyList(cappedInstanceKeys);
    if (!hasFlowScopeKey) {
      return "SELECT DISTINCT "
          + activityColumns
          + ", 'ROOT' AS attributed_via FROM "
          + SqlText.identifier(ACTIVITIES)
          + " WHERE instance_key IN ("
          + inList
          + ")";
    }
    final String typeFilter = typeFilter(query, "") + " AND instance_key IN (" + inList + ")";
    final String activities = SqlText.identifier(ACTIVITIES);
    final String objects = SqlText.identifier(OBJECTS);
    return "WITH RECURSIVE seeds AS (SELECT instance_key, scope_key, first_seen FROM "
        + objects
        + " WHERE "
        + typeFilter
        + " AND scope_key IS NOT NULL), root_first AS (SELECT instance_key, MIN(first_seen) AS"
        + " root_first_seen FROM "
        + objects
        + " WHERE "
        + typeFilter
        + " AND scope_key IS NULL GROUP BY instance_key), subtree(instance_key, element_key) AS"
        + " (SELECT s.instance_key, s.scope_key FROM seeds s LEFT JOIN root_first r ON"
        + " r.instance_key = s.instance_key WHERE r.instance_key IS NULL OR s.first_seen <"
        + " r.root_first_seen UNION ALL SELECT a.instance_key, a.element_key FROM "
        + activities
        + " a JOIN subtree t ON a.instance_key = t.instance_key AND a.flow_scope_key ="
        + " t.element_key) SELECT DISTINCT a.instance_key, a.process_id, a.element_id,"
        + " a.element_type, a.element_key, a.state, a.started_at, a.ended_at, a.duration_ms,"
        + " 'SCOPE' AS attributed_via FROM "
        + activities
        + " a JOIN subtree st ON st.instance_key = a.instance_key AND st.element_key ="
        + " a.element_key UNION ALL SELECT DISTINCT a.instance_key, a.process_id, a.element_id,"
        + " a.element_type, a.element_key, a.state, a.started_at, a.ended_at, a.duration_ms,"
        + " 'ROOT' AS attributed_via FROM "
        + activities
        + " a JOIN root_first r ON r.instance_key = a.instance_key WHERE NOT EXISTS (SELECT 1"
        + " FROM subtree st WHERE st.instance_key = a.instance_key AND st.element_key ="
        + " a.element_key)";
  }

  /**
   * The {@code instance_links} lookup for this journey's capped instance set. Bounded to at most
   * {@value #JOURNEY_INSTANCE_CAP} keys, so an inline literal {@code IN} list (rather than a
   * subquery back into {@code objects}) is both correct and cheap here -- the unbounded version of
   * this list (every sighted instance, uncapped) is exactly what this rewrite removes.
   */
  private String linksSql(final Set<Long> cappedInstanceKeys) {
    final String columns = "parent_instance_key, child_instance_key, link_type, linked_at";
    if (cappedInstanceKeys.isEmpty()) {
      return "SELECT " + columns + " FROM " + SqlText.identifier(INSTANCE_LINKS) + " WHERE FALSE";
    }
    final String inList = instanceKeyList(cappedInstanceKeys);
    return "SELECT "
        + columns
        + " FROM "
        + SqlText.identifier(INSTANCE_LINKS)
        + " WHERE parent_instance_key IN ("
        + inList
        + ") OR child_instance_key IN ("
        + inList
        + ")";
  }

  private static String instanceKeyList(final Set<Long> instanceKeys) {
    return instanceKeys.stream().map(String::valueOf).reduce((a, b) -> a + ", " + b).orElse("");
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

  /**
   * {@code POST /api/objects/list} request. {@code sort} is optional: {@code "FIRST_SEEN_DESC"}
   * (the default, and the only ordering when {@code null}) or {@code "DURATION_DESC"} (by {@code
   * duration_ms} descending, {@code NULLS LAST}) -- silently falls back to {@code FIRST_SEEN_DESC}
   * when {@code object_lifecycle} isn't available, since {@code duration_ms} doesn't exist without
   * it (never an error; same open/closed graceful-degradation rule as the rest of this class).
   */
  public record ObjectListQuery(
      String type, String status, Integer limit, Integer offset, String sort) {

    /**
     * Pre-{@code sort} 4-arg shape, kept so every existing caller (including other test files
     * outside this change's scope) keeps compiling unchanged -- equivalent to passing {@code
     * sort=null} (the default ordering).
     */
    public ObjectListQuery(
        final String type, final String status, final Integer limit, final Integer offset) {
      this(type, status, limit, offset, null);
    }
  }

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

  /**
   * One call-activity edge between two of this object's instances (an {@code instance_links} row):
   * the parent spawned the child via a call activity.
   */
  public record JourneyLink(
      long parentInstanceKey, long childInstanceKey, String linkType, String linkedAt) {}

  /** One object-to-object edge (an {@code object_relations} row); this object is on one side. */
  public record JourneyRelation(
      String parentType, String parentId, String childType, String childId, String firstSeen) {}

  /**
   * {@code POST /api/objects/journey} response. {@code sightings} (and everything derived from it
   * -- {@code activities}, {@code links}) is already capped to the {@value #JOURNEY_INSTANCE_CAP}
   * most-recently-active instances; {@code totalInstances} is the true, uncapped distinct-instance
   * count and {@code truncated} is {@code totalInstances > JOURNEY_INSTANCE_CAP} -- together they
   * let a caller show "20 of 1,500 instances" instead of silently rendering a partial journey.
   */
  public record JourneyResult(
      List<Sighting> sightings,
      List<JourneyActivity> activities,
      List<JourneyLink> links,
      List<JourneyRelation> relations,
      int totalInstances,
      boolean truncated,
      List<String> sql) {}
}
