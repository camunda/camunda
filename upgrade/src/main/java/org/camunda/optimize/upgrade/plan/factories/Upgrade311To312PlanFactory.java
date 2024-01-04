/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.plan.factories;

import org.camunda.optimize.service.db.es.schema.index.events.EventSequenceCountIndexES;
import org.camunda.optimize.upgrade.plan.UpgradeExecutionDependencies;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.steps.UpgradeStep;
import org.camunda.optimize.upgrade.steps.schema.UpdateIndexStep;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.db.DatabaseConstants.EVENT_SEQUENCE_COUNT_INDEX_PREFIX;

public class Upgrade311To312PlanFactory implements UpgradePlanFactory {

  @Override
  public UpgradePlan createUpgradePlan(UpgradeExecutionDependencies upgradeExecutionDependencies) {
    return UpgradePlanBuilder.createUpgradePlan()
      .fromVersion("3.11")
      .toVersion("3.12.0")
      .addUpgradeSteps(addEventLabelFieldToSequenceIndex(upgradeExecutionDependencies))
      .build();
  }

  private List<UpgradeStep> addEventLabelFieldToSequenceIndex(final UpgradeExecutionDependencies upgradeExecutionDependencies) {
    final Set<String> allSequenceIndexNames = upgradeExecutionDependencies.getEsClient()
      .getAllIndicesForAlias(
        upgradeExecutionDependencies.getIndexNameService().getIndexPrefix() + "-" +
          EVENT_SEQUENCE_COUNT_INDEX_PREFIX + "*"
      );
    return allSequenceIndexNames.stream()
      .map(indexName -> {
        final String indexWithoutPrefix =
          indexName.substring(upgradeExecutionDependencies.getIndexNameService().getIndexPrefix().length());
        final String keyAndVersionSuffix =
          indexWithoutPrefix.substring(indexWithoutPrefix.indexOf(EVENT_SEQUENCE_COUNT_INDEX_PREFIX) + EVENT_SEQUENCE_COUNT_INDEX_PREFIX.length());
        // We remove the last three characters, which are the version
        return keyAndVersionSuffix.substring(0, keyAndVersionSuffix.length() - 3);
      })
      .map(EventSequenceCountIndexES::new)
      .map(UpdateIndexStep::new)
      .collect(Collectors.toList());
  }

}
