/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.util;

/**
 * {@link AbstractAuroraReplicationCluster} for an Aurora <b>PostgreSQL</b> Global Database.
 *
 * <p>The admin JDBC URL (see {@value AbstractAuroraReplicationCluster#ENV_JDBC_URL}) is expected to
 * be a {@code jdbc:postgresql://<endpoint>:5432/postgres}-style URL.
 */
public final class AuroraPostgresReplicationCluster extends AbstractAuroraReplicationCluster {

  @Override
  protected String dropDatabaseStatement(final String databaseName) {
    return "DROP DATABASE IF EXISTS " + databaseName + " WITH (FORCE)";
  }

  @Override
  protected String replicaUnreachableQuery() {
    return """
        SELECT server_id, session_id, durable_lsn
        FROM aurora_global_db_instance_status()
        """;
  }

  @Override
  protected String replicaStatusQuery() {
    return """
        SELECT server_id, session_id, aws_region, durable_lsn, visibility_lag_in_msec
        FROM aurora_global_db_instance_status()
        """;
  }
}
