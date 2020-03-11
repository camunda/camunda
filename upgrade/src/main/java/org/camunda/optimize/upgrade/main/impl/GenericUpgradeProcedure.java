/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.main.impl;

import lombok.AllArgsConstructor;
import org.camunda.optimize.upgrade.main.UpgradeProcedure;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;

@AllArgsConstructor
public class GenericUpgradeProcedure extends UpgradeProcedure {
  private String fromVersion;
  private String toVersion;

  @Override
  protected UpgradePlan buildUpgradePlan() {
    return UpgradePlanBuilder.createUpgradePlan()
      .addUpgradeDependencies(upgradeDependencies)
      .fromVersion(fromVersion)
      .toVersion(toVersion)
      .build();
  }

  @Override
  public String getInitialVersion() {
    return fromVersion;
  }

  @Override
  public String getTargetVersion() {
    return toVersion;
  }
}
