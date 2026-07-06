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
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.cursor.Cursor;

/**
 * The MyBatis mapper for reading dataset cells. One provider-built dynamic {@code SELECT}; rows
 * come back as column-keyed maps (the column set is per-dataset dynamic), which {@link
 * RdbmsDatasetQueryClient} converts into neutral {@code Cell}s.
 */
public interface DatasetQueryMapper {

  @SelectProvider(type = DatasetQuerySqlProvider.class, method = "fetch")
  List<Map<String, Object>> fetch(Map<String, Object> params);

  /**
   * The same query as {@link #fetch} but returned as a lazy, forward-only {@link Cursor} so the
   * client streams cells in {@code fetchSize} batches instead of materializing the whole result.
   */
  @SelectProvider(type = DatasetQuerySqlProvider.class, method = "fetch")
  @Options(fetchSize = 1000)
  Cursor<Map<String, Object>> fetchCursor(Map<String, Object> params);

  @SelectProvider(type = TableRowSqlProvider.class, method = "fetch")
  List<Map<String, Object>> fetchRows(Map<String, Object> params);
}
