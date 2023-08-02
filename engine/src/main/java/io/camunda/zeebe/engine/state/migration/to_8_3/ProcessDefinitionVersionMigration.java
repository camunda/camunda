/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.migration.to_8_3;

import io.camunda.zeebe.engine.state.migration.MigrationTask;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;

/**
 * This migration is used to extend the data we have on process definition versions. We used to keep
 * track of only a single version. This version was the highest known version of the process
 * definition.
 *
 * <p>For resource deletion we need to know all the versions that are available in the state. This
 * migration will make sure we store a list of all known versions.
 */
public class ProcessDefinitionVersionMigration implements MigrationTask {

  @Override
  public String getIdentifier() {
    return getClass().getSimpleName();
  }

  @Override
  public void runMigration(final MutableProcessingState processingState) {
    processingState.getMigrationState().migrateProcessDefinitionVersions();
  }
}
