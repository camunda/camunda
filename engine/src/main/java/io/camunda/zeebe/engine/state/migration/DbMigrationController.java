/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.migration;

import io.camunda.zeebe.engine.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.engine.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.engine.state.mutable.MutableZeebeState;
import java.util.function.Function;

public final class DbMigrationController implements StreamProcessorLifecycleAware {

  private DbMigrator dbMigrator;
  private final Function<MutableZeebeState, DbMigrator> migratorFactory;

  public DbMigrationController() {
    this(DbMigratorImpl::new);
  }

  DbMigrationController(final Function<MutableZeebeState, DbMigrator> migratorFactory) {
    this.migratorFactory = migratorFactory;
  }

  @Override
  public final void onRecovered(final ReadonlyStreamProcessorContext context) {
    final var migrator = migratorFactory.apply(context.getZeebeState());

    synchronized (this) {
      dbMigrator = migrator;
    }
    try {
      migrator.runMigrations();
    } finally {
      synchronized (this) {
        dbMigrator = null;
      }
    }
  }

  @Override
  public final void onClose() {
    abortMigrationIfRunning();
  }

  @Override
  public final void onFailed() {
    abortMigrationIfRunning();
  }

  private void abortMigrationIfRunning() {
    synchronized (this) {
      if (dbMigrator != null) {
        dbMigrator.abort();
      }
    }
  }
}
