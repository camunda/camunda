/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.web;

import io.camunda.analytics.lake.serving.duckdb.LakeQueryService;
import io.camunda.analytics.lake.serving.duckdb.LakeQueryService.QueryResult;
import io.camunda.analytics.lake.serving.duckdb.LakeViewRegistry;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Proof-of-life REST surface over the discovered lake views: health, the discovered table list with
 * row counts, and a free-form SQL endpoint — the same POST-plain-SQL convention {@code
 * analytics-lake}'s demo UI uses. The explain-shaped endpoints (follow-up lanes I/J) build on this
 * structure rather than replacing it.
 */
@RestController
@RequestMapping("/api")
public class LakeController {

  private final LakeViewRegistry viewRegistry;
  private final LakeQueryService queryService;

  public LakeController(final LakeViewRegistry viewRegistry, final LakeQueryService queryService) {
    this.viewRegistry = viewRegistry;
    this.queryService = queryService;
  }

  @GetMapping("/health")
  public Map<String, String> health() {
    return Map.of("status", "UP");
  }

  /** Every discovered lake view with its current row count (best-effort; see {@link TableInfo}). */
  @GetMapping("/tables")
  public List<TableInfo> tables() {
    return viewRegistry.registeredTables().stream().map(this::describe).toList();
  }

  /**
   * Re-runs view discovery against the warehouse directory and returns the resulting view names —
   * for a table that appeared after startup (or after a prior request already triggered the same
   * lazy refresh once; see {@link LakeViewRegistry#ensureAvailable}) to become visible without a
   * restart.
   */
  @PostMapping("/refresh")
  public List<String> refresh() {
    return viewRegistry.refresh();
  }

  private TableInfo describe(final String tableName) {
    final String countSql = "SELECT count(*) FROM \"" + tableName.replace("\"", "\"\"") + "\"";
    try {
      final Optional<Object> count = queryService.executeScalar(countSql);
      return new TableInfo(
          tableName, count.map(value -> ((Number) value).longValue()).orElse(null));
    } catch (final SQLException e) {
      // A registered view failing to count (e.g. its backing file vanished after startup) is
      // reported as "unknown", not a 500 for the whole endpoint.
      return new TableInfo(tableName, null);
    }
  }

  /** Plain SQL in the request body, JSON columns/rows out — capped at the configured row limit. */
  @PostMapping(value = "/query", consumes = MediaType.TEXT_PLAIN_VALUE)
  public QueryResponse query(@RequestBody final String sql) {
    if (sql == null || sql.isBlank()) {
      throw new IllegalArgumentException("Empty query");
    }
    try {
      final QueryResult result = queryService.execute(sql);
      return new QueryResponse(result.columns(), result.rows());
    } catch (final SQLException e) {
      throw new LakeQueryException(e.getMessage(), e);
    }
  }

  @ExceptionHandler(LakeQueryException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Map<String, String> onQueryError(final LakeQueryException e) {
    return Map.of("error", e.getMessage() == null ? "unknown error" : e.getMessage());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Map<String, String> onInvalid(final IllegalArgumentException e) {
    return Map.of("error", e.getMessage());
  }

  /**
   * One discovered lake table: its view name and current row count ({@code null} if unavailable).
   */
  public record TableInfo(String name, Long rowCount) {}

  /** {@code POST /api/query} response body. */
  public record QueryResponse(List<String> columns, List<List<Object>> rows) {}
}
