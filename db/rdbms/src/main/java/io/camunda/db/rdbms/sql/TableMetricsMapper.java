/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql;

import java.util.List;
import org.apache.ibatis.annotations.Param;

/** Mapper for table metrics operations. */
public interface TableMetricsMapper {
  /**
   * Counts the rows of each of the given tables in a single round trip. A table missing from the
   * catalog is absent from the result rather than returned with {@code -1}.
   *
   * @param tableNames the prefixed, already case-folded table identifiers to look up
   */
  List<TableRowCount> countTableRows(@Param("tableNames") List<String> tableNames);

  /**
   * Counts the rows of a single table with a live {@code COUNT(*)}, for vendors without catalog
   * row-count statistics.
   *
   * @param tableName the prefixed, already case-folded table identifier to count
   */
  long countSingleTableRows(String tableName);

  /** {@code tableName} is the searched-for identifier, not the bare table name. */
  record TableRowCount(String tableName, Long rowCount) {}
}
