/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.encoding;

/**
 * The migration status of a single partition replica, as reported over the {@code
 * GET_MIGRATION_STATUS} admin request/response wire protocol (see {@link PartitionMigrationStatus},
 * {@link MigrationStatusPayload}).
 *
 * <p>Deliberately distinct from {@code io.camunda.cluster.MigrationState}: that type is the
 * upgrade-readiness API/SPI's condition state, used by every {@code MigrationStatusProvider} and
 * the actuator endpoint that aggregates them. This one is the wire-level status a broker reports
 * for one partition replica. Keeping them separate means the broker/gateway RPC layer does not
 * depend on the {@code cluster} API module, and the API-level enum can evolve independently of the
 * wire format. Providers map between the two at the boundary (e.g. {@code
 * ClusterRocksDbMigrationStatusProvider}, once it has aggregated across every replica).
 */
public enum MigrationStatusCode {
  MIGRATED,
  MIGRATION_IN_PROGRESS,
  UNKNOWN
}
