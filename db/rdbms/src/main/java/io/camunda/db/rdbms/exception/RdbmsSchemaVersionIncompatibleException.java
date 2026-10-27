/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.exception;

/**
 * Thrown when the RDBMS schema version is incompatible with the running application version.
 *
 * <p>This exception is raised during schema initialization when the upgrade path is illegal, for
 * example when trying to skip minor versions (e.g. 8.9.x → 8.11.y). Only sequential minor-version
 * upgrades are supported (e.g. 8.9.x → 8.9.y or 8.9.x → 8.10.y).
 *
 * <p>This check is only enforced when auto-DDL is enabled (i.e. {@code LiquibaseSchemaManager} is
 * active). When auto-DDL is disabled ({@code NoopSchemaManager}), no version check is performed.
 */
public class RdbmsSchemaVersionIncompatibleException extends RuntimeException {

  public RdbmsSchemaVersionIncompatibleException(
      final String schemaVersion, final String appVersion) {
    super(
        "Detected RDBMS schema version %s, but application is %s. "
                .formatted(schemaVersion, appVersion)
            + "Upgrades may only go from %s.x to %s.y or the next minor version. "
                .formatted(schemaVersion, schemaVersion)
            + "Skipping minor versions is not supported.");
  }
}
