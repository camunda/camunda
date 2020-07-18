/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.main.impl;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.es.schema.index.AlertIndex;
import org.camunda.optimize.upgrade.main.UpgradeProcedure;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.steps.UpgradeStep;
import org.camunda.optimize.upgrade.steps.schema.UpdateIndexStep;

@Slf4j
public class UpgradeFrom31To32 extends UpgradeProcedure {
  public static final String FROM_VERSION = "3.1.0";
  public static final String TO_VERSION = "3.2.0";

  @Override
  public String getInitialVersion() {
    return FROM_VERSION;
  }

  @Override
  public String getTargetVersion() {
    return TO_VERSION;
  }

  public UpgradePlan buildUpgradePlan() {
    final UpgradePlanBuilder.AddUpgradeStepBuilder upgradeBuilder = UpgradePlanBuilder.createUpgradePlan()
      .addUpgradeDependencies(upgradeDependencies)
      .fromVersion(FROM_VERSION)
      .toVersion(TO_VERSION)
      .addUpgradeStep(migrateAlertEmailRecipientsField());
    return upgradeBuilder.build();
  }

  private UpgradeStep migrateAlertEmailRecipientsField() {
    //@formatter:off
    final String script =
      "def emails = new ArrayList();\n" +
      "if (ctx._source.email != null) {\n" +
        "emails.add(ctx._source.email);\n" +
      "}\n" +
      "ctx._source.emails = emails;\n" +
      "ctx._source.remove(\"email\");"
      ;
    //@formatter:on
    return new UpdateIndexStep(
      new AlertIndex(),
      script
    );
  }

}
