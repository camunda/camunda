/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.api;

import org.jspecify.annotations.Nullable;

/** Read-only access to the migration status stored in a partition's state. */
public interface MigrationStatusReader {

  /** Returns the version that last migrated the state, or {@code null} if none is recorded. */
  @Nullable String getMigratedByVersion();
}
