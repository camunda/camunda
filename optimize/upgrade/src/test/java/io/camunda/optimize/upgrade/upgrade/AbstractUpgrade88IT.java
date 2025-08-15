/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.upgrade;

import io.camunda.optimize.service.util.configuration.DatabaseType;
import io.camunda.optimize.upgrade.AbstractUpgradeIT;
import io.camunda.optimize.upgrade.db.indices.VariableUpdateInstanceIndexOld;
import io.camunda.optimize.upgrade.es.indices.VariableUpdateInstanceIndexOldES;
import io.camunda.optimize.upgrade.os.indices.VariableUpdateInstanceIndexOldOS;
import io.camunda.optimize.upgrade.plan.UpgradePlan;
import io.camunda.optimize.upgrade.plan.UpgradePlanRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;

public class AbstractUpgrade88IT extends AbstractUpgradeIT {

  protected static final String FROM_VERSION = "8.7.0";
  protected static final String TO_VERSION = "8.8.0";

  protected VariableUpdateInstanceIndexOld variableUpdateInstanceIndexOld;

  @Override
  @BeforeEach
  protected void setUp() throws Exception {
    super.setUp();
    instantiateProperIndices(databaseIntegrationTestExtension.getDatabaseVendor());
    initSchema(List.of(variableUpdateInstanceIndexOld));
    setMetadataVersion(FROM_VERSION);
  }

  protected void performUpgrade() {
    final List<UpgradePlan> upgradePlans =
        new UpgradePlanRegistry(upgradeDependencies)
            .getSequentialUpgradePlansToTargetVersion(TO_VERSION);
    upgradePlans.forEach(plan -> upgradeProcedure.performUpgrade(plan));
  }

  private void instantiateProperIndices(final DatabaseType databaseVendor) {
    if (!isElasticSearchUpgrade()) {
      variableUpdateInstanceIndexOld = new VariableUpdateInstanceIndexOldOS();
    } else {
      variableUpdateInstanceIndexOld = new VariableUpdateInstanceIndexOldES();
    }
  }
}
