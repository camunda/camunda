/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.encoding;

/**
 * A single partition replica's migration status, reported by {@code
 * PartitionAdminAccess#getMigrationStatus()} and carried over the wire by {@link
 * MigrationStatusPayload}.
 *
 * @param code whether this replica is migrated, confidently not yet migrated, or unknown
 * @param detail a short, human-readable explanation
 */
public record PartitionMigrationStatus(MigrationStatusCode code, String detail) {}
