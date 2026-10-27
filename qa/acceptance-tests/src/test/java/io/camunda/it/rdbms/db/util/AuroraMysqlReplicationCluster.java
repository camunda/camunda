/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.util;

/**
 * {@link AbstractAuroraReplicationCluster} for an Aurora <b>MySQL</b> Global Database.
 *
 * <p>The admin JDBC URL (see {@value AbstractAuroraReplicationCluster#ENV_JDBC_URL}) is expected to
 * be a {@code jdbc:mysql://<endpoint>:3306/mysql}-style URL.
 *
 * <p>Aurora MySQL exposes Global Database instance status as the {@code
 * information_schema.aurora_global_db_instance_status} table rather than the {@code
 * aurora_global_db_instance_status()} table function used by Aurora PostgreSQL (see {@link
 * AuroraPostgresReplicationCluster}). Column name case differs (upper-case on MySQL) but JDBC
 * {@code ResultSet} column-label lookups are case-insensitive, so the shared parsing logic in
 * {@link AbstractAuroraReplicationCluster} works unchanged.
 */
public final class AuroraMysqlReplicationCluster extends AbstractAuroraReplicationCluster {

  @Override
  protected String dropDatabaseStatement(final String databaseName) {
    // unlike PostgreSQL, MySQL does not require terminating open sessions to drop a database
    return "DROP DATABASE IF EXISTS " + databaseName;
  }

  @Override
  protected String replicaUnreachableQuery() {
    return """
        SELECT SERVER_ID, SESSION_ID, DURABLE_LSN
        FROM information_schema.aurora_global_db_instance_status
        """;
  }

  @Override
  protected String replicaStatusQuery() {
    return """
        SELECT SERVER_ID, SESSION_ID, AWS_REGION, DURABLE_LSN, VISIBILITY_LAG_IN_MSEC
        FROM information_schema.aurora_global_db_instance_status
        """;
  }
}
