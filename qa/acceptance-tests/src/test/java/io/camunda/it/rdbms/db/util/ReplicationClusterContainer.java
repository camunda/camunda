/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.util;

/**
 * Abstraction over a containerised database replication cluster used in acceptance tests.
 *
 * <p>Both {@link PostgresReplicationClusterContainer} and {@link MSSQLReplicationClusterContainer}
 * implement this interface so that {@link CamundaRdbmsTestApplication} and the async-replication
 * test base class can treat them uniformly.
 */
public interface ReplicationClusterContainer extends AutoCloseable {

  /** Starts the cluster (primary + replica). */
  void start();

  /** Stops the cluster. */
  void stop();

  /** JDBC URL pointing at the primary database. */
  String getJdbcUrl();

  /** Database username for the primary. */
  String getUsername();

  /** Database password for the primary. */
  String getPassword();

  /** Stops only the replica, leaving the primary running. */
  void stopReplica();

  /** (Re-)starts the replica and waits for it to sync with the primary. */
  void startReplica();
}
