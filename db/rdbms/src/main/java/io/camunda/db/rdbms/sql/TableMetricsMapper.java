/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql;

/** Mapper for table metrics operations. */
public interface TableMetricsMapper {

  /**
   * Counts the number of rows in the specified table.
   *
   * @param tableName the name of the table to count rows in
   * @return the number of rows in the table
   */
  long countTableRows(String tableName);
}
