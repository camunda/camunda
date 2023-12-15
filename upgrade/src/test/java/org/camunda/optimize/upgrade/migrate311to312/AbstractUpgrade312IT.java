/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.migrate311to312;

import org.camunda.optimize.upgrade.AbstractUpgradeIT;
import org.camunda.optimize.upgrade.migrate311to312.indices.EventSequenceCountIndexESV3;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanRegistry;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;

import static org.camunda.optimize.service.db.DatabaseConstants.EXTERNAL_EVENTS_INDEX_SUFFIX;

public abstract class AbstractUpgrade312IT extends AbstractUpgradeIT {

  protected static final String FROM_VERSION = "3.11.0";
  protected static final String TO_VERSION = "3.12.0";
  public static final String TEST_PROCESS_KEY = "testprocess";
  protected final EventSequenceCountIndexESV3 EXTERNAL_EVENT_SEQUENCE_COUNT_V3
    = new EventSequenceCountIndexESV3(EXTERNAL_EVENTS_INDEX_SUFFIX);
  protected final EventSequenceCountIndexESV3 CAMUNDA_EVENT_SEQUENCE_COUNT_V3
    = new EventSequenceCountIndexESV3(TEST_PROCESS_KEY);

  @BeforeEach
  protected void setUp() throws Exception {
    super.setUp();
    initSchema(List.of(
      EXTERNAL_EVENT_SEQUENCE_COUNT_V3,
      CAMUNDA_EVENT_SEQUENCE_COUNT_V3
    ));
    setMetadataVersion(FROM_VERSION);
  }

  protected void performUpgrade() {
    final List<UpgradePlan> upgradePlans =
      new UpgradePlanRegistry(upgradeDependencies).getSequentialUpgradePlansToTargetVersion(TO_VERSION);
    upgradePlans.forEach(plan -> upgradeProcedure.performUpgrade(plan));
  }

}
