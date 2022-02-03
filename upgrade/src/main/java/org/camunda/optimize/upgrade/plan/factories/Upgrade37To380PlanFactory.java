/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.plan.factories;

import org.camunda.optimize.service.es.schema.index.index.PositionBasedImportIndex;
import org.camunda.optimize.upgrade.plan.UpgradeExecutionDependencies;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.steps.schema.UpdateIndexStep;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Map;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;

public class Upgrade37To380PlanFactory implements UpgradePlanFactory {

  @Override
  public UpgradePlan createUpgradePlan(final UpgradeExecutionDependencies dependencies) {
    return UpgradePlanBuilder.createUpgradePlan()
      .fromVersion("3.7")
      .toVersion("3.8.0")
      .addUpgradeStep(addLastEntityTimestampToPositionBasedImportIndices())
      .build();
  }

  private static UpdateIndexStep addLastEntityTimestampToPositionBasedImportIndices() {
    return new UpdateIndexStep(
      new PositionBasedImportIndex(),
      "ctx._source.timestampOfLastEntity = params.beginningOfTime;",
      Map.of("beginningOfTime", DateTimeFormatter.ofPattern(OPTIMIZE_DATE_FORMAT)
        .format(ZonedDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault()))),
      Collections.emptySet()
    );
  }

}
