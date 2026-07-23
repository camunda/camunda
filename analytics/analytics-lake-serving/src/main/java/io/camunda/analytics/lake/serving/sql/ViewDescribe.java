/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.sql;

import io.camunda.analytics.lake.serving.duckdb.LakeQueryService;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code DESCRIBE <view>} in physical column order, for services that validate a caller-supplied
 * column name against a view's real schema rather than a hardcoded list — the same "never hardcode
 * the shape, ask the view" spirit as {@link
 * io.camunda.analytics.lake.serving.duckdb.LakeViewRegistry} itself.
 */
public final class ViewDescribe {

  private ViewDescribe() {}

  public static List<String> columns(final LakeQueryService queryService, final String view)
      throws SQLException {
    final LakeQueryService.QueryResult result =
        queryService.execute("DESCRIBE " + SqlText.identifier(view));
    final List<String> columns = new ArrayList<>(result.rows().size());
    for (final List<Object> row : result.rows()) {
      columns.add((String) row.get(0));
    }
    return columns;
  }
}
