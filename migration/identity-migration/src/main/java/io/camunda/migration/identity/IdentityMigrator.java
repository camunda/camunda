/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity;

import io.camunda.migration.api.Migrator;
import io.camunda.migration.identity.config.IdentityMigrationProperties;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@EnableConfigurationProperties(IdentityMigrationProperties.class)
@Component("identity-migrator")
public class IdentityMigrator implements Migrator {

  private final List<MigrationHandler<?>> handlers;

  public IdentityMigrator(final List<MigrationHandler<?>> handlers) {
    this.handlers = handlers;
  }

  @Override
  public Void call() {
    migrate();
    return null;
  }

  private void migrate() {
    for (final MigrationHandler<?> handler : handlers) {
      handler.migrate();
    }
  }
}
