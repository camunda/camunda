/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.migration.to_8_3;

import io.camunda.zeebe.engine.state.migration.MigrationTask;
import io.camunda.zeebe.engine.state.migration.MigrationTaskContext;
import io.camunda.zeebe.engine.state.migration.MutableMigrationTaskContext;

/**
 * This migration is used to initially fill the PROCESS_INSTANCE_KEY_BY_DEFINITION_KEY ColumnFamily.
 * It will go over all the element instances, check if they are of the BpmnElementType PROCESS, and
 * if they are insert them into the ColumnFamily.
 */
public class ProcessInstanceByProcessDefinitionMigration implements MigrationTask {

  @Override
  public String getIdentifier() {
    return getClass().getSimpleName();
  }

  @Override
  public boolean needsToRun(final MigrationTaskContext context) {
    return context
        .processingState()
        .getMigrationState()
        .shouldRunElementInstancePopulateProcessInstanceByDefinitionKey();
  }

  @Override
  public void runMigration(final MutableMigrationTaskContext context) {
    context
        .processingState()
        .getMigrationState()
        .migrateElementInstancePopulateProcessInstanceByDefinitionKey();
  }
}
