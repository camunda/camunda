/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.migration.to_8_2;

import io.camunda.zeebe.engine.state.migration.MigrationTask;
import io.camunda.zeebe.engine.state.migration.MigrationTaskContext;
import io.camunda.zeebe.engine.state.migration.MutableMigrationTaskContext;
import io.camunda.zeebe.protocol.ZbColumnFamilies;

public class DecisionRequirementsMigration implements MigrationTask {

  @Override
  public String getIdentifier() {
    return getClass().getSimpleName();
  }

  @Override
  public boolean needsToRun(final MigrationTaskContext context) {
    return context
            .processingState()
            .isEmpty(
                ZbColumnFamilies
                    .DEPRECATED_DMN_DECISION_REQUIREMENTS_KEY_BY_DECISION_REQUIREMENT_ID_AND_VERSION)
        && !context
            .processingState()
            .isEmpty(ZbColumnFamilies.DEPRECATED_DMN_DECISION_REQUIREMENTS);
  }

  @Override
  public void runMigration(final MutableMigrationTaskContext context) {
    context.processingState().getMigrationState().migrateDrgPopulateDrgVersionByDrgIdAndKey();
  }
}
