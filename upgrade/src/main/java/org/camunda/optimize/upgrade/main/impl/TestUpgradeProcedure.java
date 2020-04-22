/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.main.impl;

import org.camunda.optimize.dto.optimize.query.MetadataDto;
import org.camunda.optimize.upgrade.main.UpgradeProcedure;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.util.UpgradeUtil;

public class TestUpgradeProcedure extends UpgradeProcedure {

  private final String fromVersion;
  private final String toVersion;

  public TestUpgradeProcedure(String fromVersion, String toVersion, String customConfigLocation) {
    super(UpgradeUtil.createUpgradeDependenciesWithAdditionalConfigLocation(
      "service-config.yaml", "environment-config.yaml", customConfigLocation));
    this.fromVersion = fromVersion;
    this.toVersion = toVersion;
  }

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

  public void setMetadataVersionInElasticSearch(String version) {
    upgradeDependencies.getMetadataService().writeMetadata(upgradeDependencies.getEsClient(), new MetadataDto(version));
  }

}
