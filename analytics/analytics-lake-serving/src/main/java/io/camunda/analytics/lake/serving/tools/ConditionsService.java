/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.tools;

import io.camunda.analytics.lake.serving.duckdb.LakeQueryService;
import io.camunda.analytics.lake.serving.duckdb.LakeQueryService.QueryResult;
import io.camunda.analytics.lake.serving.duckdb.LakeViewRegistry;
import io.camunda.analytics.lake.serving.sql.SqlText;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * {@code POST /api/tools/conditions}: a plain dictionary lookup in the {@code variants} view — v1
 * ("rung-4 v1") reports only the variant's own element/flow id lists and first-seen time; parsing
 * BPMN gateway expressions to explain <em>why</em> a flow was taken is a later phase, not this one.
 *
 * <p>{@code elements}/{@code flows} are stored as a single newline-joined, UTF-8-encoded {@code
 * BLOB} (see {@code analytics-lake}'s {@code LakeTranslator#emitVariantDictionaryRowIfNew}) —
 * decoded here via DuckDB's {@code decode(blob)} (a raw UTF-8 reinterpretation), <b>not</b> {@code
 * CAST(blob AS VARCHAR)}: that cast instead renders non-printable bytes (including the {@code \n}
 * separator itself) as a literal {@code \xHH}-escaped display string, which would silently defeat
 * splitting on a real newline. Split on {@code \n}, with the empty-list-encodes-as-one-empty-string
 * edge case (an element/flow-free variant) filtered out rather than reported as a single blank
 * entry.
 */
@Service
public class ConditionsService {

  private static final String VARIANTS = "variants";

  private final LakeViewRegistry viewRegistry;
  private final LakeQueryService queryService;

  public ConditionsService(
      final LakeViewRegistry viewRegistry, final LakeQueryService queryService) {
    this.viewRegistry = viewRegistry;
    this.queryService = queryService;
  }

  public ConditionsResult conditions(final ConditionsQuery query) {
    viewRegistry.ensureAvailable(VARIANTS);
    final String sql =
        "SELECT decode(elements) AS elements, decode(flows) AS flows, first_seen"
            + " FROM "
            + SqlText.identifier(VARIANTS)
            + " WHERE process_id = "
            + SqlText.literal(query.processId())
            + " AND variant_hash = "
            + SqlText.literal(query.variantHash())
            + " LIMIT 1";
    try {
      final QueryResult result = queryService.execute(sql);
      if (result.rows().isEmpty()) {
        throw new IllegalArgumentException(
            "No variant found for process_id="
                + query.processId()
                + ", variant_hash="
                + query.variantHash());
      }
      final List<Object> row = result.rows().get(0);
      final List<String> elements = splitIds((String) row.get(0));
      final List<String> flows = splitIds((String) row.get(1));
      final String firstSeen = SqlText.toIsoString(row.get(2));
      return new ConditionsResult(elements, flows, firstSeen, List.of(sql));
    } catch (final SQLException e) {
      throw new IllegalStateException("Conditions query failed: " + e.getMessage(), e);
    }
  }

  private static List<String> splitIds(final String joined) {
    if (joined == null || joined.isEmpty()) {
      return List.of();
    }
    final List<String> ids = new ArrayList<>();
    for (final String id : joined.split("\n")) {
      if (!id.isEmpty()) {
        ids.add(id);
      }
    }
    return ids;
  }

  /** {@code POST /api/tools/conditions} response. */
  public record ConditionsResult(
      List<String> elements, List<String> flows, String firstSeen, List<String> sql) {}
}
