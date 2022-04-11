/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.migrate37to38;

import org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex;
import org.camunda.optimize.upgrade.AbstractUpgradeIT;
import org.camunda.optimize.upgrade.migrate37to38.indices.PositionBasedImportIndexOld;
import org.camunda.optimize.upgrade.migrate37to38.indices.SettingsIndexOld;
import org.camunda.optimize.upgrade.migrate37to38.indices.SingleDecisionReportIndexOld;
import org.camunda.optimize.upgrade.migrate37to38.indices.SingleProcessReportIndexOld;
import org.camunda.optimize.upgrade.migrate37to38.indices.TimestampBasedImportIndexOld;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanRegistry;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;

public abstract class AbstractUpgrade37IT extends AbstractUpgradeIT {

  private static final String FROM_VERSION = "3.7.0";
  private static final String TO_VERSION = "3.8.0";

  protected static final PositionBasedImportIndexOld POSITION_BASED_INDEX = new PositionBasedImportIndexOld();
  protected static final TimestampBasedImportIndexOld TIMESTAMP_BASED_IMPORT_INDEX =
    new TimestampBasedImportIndexOld();
  protected static final SingleProcessReportIndexOld SINGLE_PROCESS_REPORT_INDEX =
    new SingleProcessReportIndexOld();
  protected static final SingleDecisionReportIndexOld SINGLE_DECISION_REPORT_INDEX =
    new SingleDecisionReportIndexOld();
  protected static final DecisionInstanceIndex FIRST_DECISION_INSTANCE_INDEX =
    new DecisionInstanceIndex("firstprocess");
  protected static final DecisionInstanceIndex SECOND_DECISION_INSTANCE_INDEX =
    new DecisionInstanceIndex("secondprocess");
  protected static final SettingsIndexOld SETTINGS_INDEX =
    new SettingsIndexOld();

  @BeforeEach
  protected void setUp() throws Exception {
    super.setUp();
    initSchema(List.of(
      POSITION_BASED_INDEX,
      TIMESTAMP_BASED_IMPORT_INDEX,
      SINGLE_PROCESS_REPORT_INDEX,
      SINGLE_DECISION_REPORT_INDEX,
      FIRST_DECISION_INSTANCE_INDEX,
      SECOND_DECISION_INSTANCE_INDEX,
      SETTINGS_INDEX
    ));
    setMetadataVersion(FROM_VERSION);
  }

  protected void performUpgrade() {
    final List<UpgradePlan> upgradePlans =
      new UpgradePlanRegistry(upgradeDependencies).getSequentialUpgradePlansToTargetVersion(TO_VERSION);
    upgradePlans.forEach(plan -> upgradeProcedure.performUpgrade(plan));
  }

}
