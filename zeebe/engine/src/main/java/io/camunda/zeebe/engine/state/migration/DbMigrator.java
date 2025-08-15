/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.migration;

import java.util.List;

public interface DbMigrator {

  /**
   * @return the number of migrations performed by type
   */
  MigrationsPerformed runMigrations();

  record MigrationsPerformed(int initializations, int migrations, boolean versionChanged) {
    public static MigrationsPerformed none() {
      return new MigrationsPerformed(0, 0, false);
    }

    public static MigrationsPerformed fromList(final List<MigrationTask> executedMigrations) {
      var initializations = 0;
      var migrations = 0;
      for (final var migration : executedMigrations) {
        if (migration.isInitialization()) {
          initializations++;
        } else {
          migrations++;
        }
      }
      return new MigrationsPerformed(initializations, migrations, true);
    }
  }
}
