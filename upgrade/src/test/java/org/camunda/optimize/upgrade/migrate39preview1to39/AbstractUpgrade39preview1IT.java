/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.migrate39preview1to39;

import org.camunda.optimize.upgrade.AbstractUpgradeIT;
import org.camunda.optimize.upgrade.migrate39preview1to39.indices.EventProcessDefinitionIndex39preview1;
import org.camunda.optimize.upgrade.migrate39preview1to39.indices.ProcessDefinitionIndex39preview1;
import org.camunda.optimize.upgrade.migrate39preview1to39.indices.ProcessGoalIndex39Preview1;
import org.camunda.optimize.upgrade.migrate39preview1to39.indices.ProcessOverviewIndex39preview1;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanRegistry;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;

public class AbstractUpgrade39preview1IT extends AbstractUpgradeIT {

  protected static final String FROM_VERSION = "3.9.0-preview-1";
  protected static final String TO_VERSION = "3.9.0";

  protected static final ProcessOverviewIndex39preview1 PROCESS_OVERVIEW_INDEX = new ProcessOverviewIndex39preview1();
  protected static final ProcessGoalIndex39Preview1 PROCESS_GOALS_INDEX = new ProcessGoalIndex39Preview1();
  protected static final ProcessDefinitionIndex39preview1 PROCESS_DEFINITION_INDEX = new ProcessDefinitionIndex39preview1();
  protected static final EventProcessDefinitionIndex39preview1 EVENT_PROCESS_DEFINITION_INDEX =
    new EventProcessDefinitionIndex39preview1();

  @BeforeEach
  protected void setUp() throws Exception {
    super.setUp();
    initSchema(List.of(
      PROCESS_OVERVIEW_INDEX,
      PROCESS_GOALS_INDEX,
      PROCESS_DEFINITION_INDEX,
      EVENT_PROCESS_DEFINITION_INDEX
    ));
    setMetadataVersion(FROM_VERSION);
  }

  protected void performUpgrade() {
    final List<UpgradePlan> upgradePlans =
      new UpgradePlanRegistry(upgradeDependencies).getSequentialUpgradePlansToTargetVersion(TO_VERSION);
    upgradePlans.forEach(plan -> upgradeProcedure.performUpgrade(plan));
  }
}
