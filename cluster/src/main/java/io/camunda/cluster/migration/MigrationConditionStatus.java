/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.cluster.migration;

import org.jspecify.annotations.NullMarked;

/**
 * The status of a single upgrade-readiness condition, as reported by a {@link
 * MigrationStatusProvider}.
 *
 * @param state whether the condition is met, confidently not yet met, or unknown
 * @param detail a short, human-readable explanation, surfaced as-is in the upgrade-readiness
 *     endpoint (e.g. {@code "schema version 8.9.0 has not yet migrated to 8.10.0"})
 */
@NullMarked
public record MigrationConditionStatus(MigrationState state, String detail) {}
