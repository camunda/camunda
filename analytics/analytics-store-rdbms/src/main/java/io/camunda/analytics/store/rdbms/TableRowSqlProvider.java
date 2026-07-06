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
 * Builds the dynamic {@code SELECT} for a {@link io.camunda.analytics.serving.spi.TableFetch} using
 * MyBatis's {@link SQL} builder: the declared columns of one {@code projection_<id>} row table,
 * with equality predicates on declared columns and a row {@code LIMIT}. Identifiers come from the
 * compiled schema (sanitised); filter values bind through {@code #{}} placeholders, so this is
 * dynamic structure with safe value binding. The {@code limit} is a validated {@code int} (never
 * user text), so it is inlined — MyBatis's {@link SQL} builder has no {@code LIMIT} clause.
 */
public final class TableRowSqlProvider {

  @SuppressWarnings("unchecked")
  public String fetch(final Map<String, Object> params) {
    final String table = (String) params.get("table");
    final List<String> columns = (List<String>) params.get("columns");
    final List<String> filterColumns = (List<String>) params.get("filterColumns");
    final int limit = ((Number) params.get("limit")).intValue();

    final SQL sql = new SQL();
    columns.forEach(sql::SELECT);
    sql.FROM(table);
    for (int i = 0; i < filterColumns.size(); i++) {
      sql.WHERE(filterColumns.get(i) + " = #{filterValues[" + i + "]}");
    }
    return sql.toString() + " LIMIT " + limit;
  }
}
