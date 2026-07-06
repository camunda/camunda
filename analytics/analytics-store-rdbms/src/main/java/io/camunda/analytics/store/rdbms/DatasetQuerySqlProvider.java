/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.rdbms;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.jdbc.SQL;

/**
 * Builds the dynamic {@code SELECT} for a {@link io.camunda.analytics.dataset.store.DatasetFetch}
 * using MyBatis's {@link SQL} builder: the grain dimension columns + the requested meter blob
 * columns of one {@code dataset_<id>} table, filtered to the tier and window range, with equality
 * predicates on grain columns. Identifiers come from the compiled schema (sanitised); values bind
 * through {@code #{}} placeholders (the tier, range, and each filter value), so this is dynamic
 * structure with safe value binding — the reason the read path uses MyBatis.
 */
public final class DatasetQuerySqlProvider {

  @SuppressWarnings("unchecked")
  public String fetch(final Map<String, Object> params) {
    final String table = (String) params.get("table");
    final List<String> dimColumns = (List<String>) params.get("dimColumns");
    final List<String> meterColumns = (List<String>) params.get("meterColumns");
    final List<String> filterColumns = (List<String>) params.get("filterColumns");

    final SQL sql = new SQL();
    sql.SELECT("window_start");
    dimColumns.forEach(sql::SELECT);
    meterColumns.forEach(sql::SELECT);
    sql.FROM(table);
    sql.WHERE("window_size = #{windowSize}");
    sql.WHERE("window_start >= #{fromMs}");
    sql.WHERE("window_start < #{toMs}");
    for (int i = 0; i < filterColumns.size(); i++) {
      sql.WHERE(filterColumns.get(i) + " = #{filterValues[" + i + "]}");
    }
    return sql.toString();
  }

  /**
   * Builds the pushed-down {@code GROUP BY} for an {@link
   * io.camunda.analytics.dataset.store.AggregatedFetch}: the group-by dimension columns plus the
   * derived time bucket ({@code window_start − MOD(window_start, :granularity)}), with a {@code
   * SUM}/{@code MIN}/{@code MAX} per additive column — the store does the reduction and returns one
   * finalized row per {@code (group, bucket)}. {@code selectColumns} carry the pre-built
   * (sanitised) dimension columns, the bucket expression, and the aggregate expressions; {@code
   * groupByColumns} repeat the dimension columns and the bucket expression. Values still bind
   * through {@code #{}}.
   */
  @SuppressWarnings("unchecked")
  public String pushDown(final Map<String, Object> params) {
    final String table = (String) params.get("table");
    final List<String> selectColumns = (List<String>) params.get("selectColumns");
    final List<String> groupByColumns = (List<String>) params.get("groupByColumns");
    final List<String> filterColumns = (List<String>) params.get("filterColumns");

    final SQL sql = new SQL();
    selectColumns.forEach(sql::SELECT);
    sql.FROM(table);
    sql.WHERE("window_size = #{windowSize}");
    sql.WHERE("window_start >= #{fromMs}");
    sql.WHERE("window_start < #{toMs}");
    for (int i = 0; i < filterColumns.size(); i++) {
      sql.WHERE(filterColumns.get(i) + " = #{filterValues[" + i + "]}");
    }
    groupByColumns.forEach(sql::GROUP_BY);
    return sql.toString();
  }
}
