/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.encoding;

/**
 * The migration status of a single partition replica, as reported over an admin request/response
 * (see {@link PartitionMigrationStatus}, {@link MigrationStatusPayload}) — shared by every
 * migration-status admin request ({@code GET_MIGRATION_STATUS}, {@code
 * GET_EXPORTING_MIGRATION_STATUS}), not specific to one.
 */
public enum MigrationStatusCode {
  MIGRATED,
  MIGRATION_IN_PROGRESS,
  UNKNOWN
}
