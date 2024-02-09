/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.plan.factories;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.utils.StringUtils;
import org.camunda.optimize.service.db.es.schema.index.MetadataIndexES;
import org.camunda.optimize.service.util.configuration.OptimizeProfile;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import org.camunda.optimize.upgrade.plan.UpgradeExecutionDependencies;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.steps.UpgradeStep;
import org.camunda.optimize.upgrade.steps.schema.UpdateIndexStep;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.client.core.CountResponse;

import java.io.IOException;

import static org.camunda.optimize.service.db.DatabaseConstants.POSITION_BASED_IMPORT_INDEX_NAME;

@Slf4j
public class Upgrade312To313PlanFactory implements UpgradePlanFactory {

  @Override
  public UpgradePlan createUpgradePlan(UpgradeExecutionDependencies upgradeExecutionDependencies) {
    return UpgradePlanBuilder.createUpgradePlan()
      .fromVersion("3.12")
      .toVersion("3.13.0")
      .addUpgradeStep(addOptimizeProfileFieldToMetadataIndex(upgradeExecutionDependencies))
      .build();
  }

  private UpgradeStep addOptimizeProfileFieldToMetadataIndex(UpgradeExecutionDependencies upgradeExecutionDependencies) {
    OptimizeProfile optimizeProfile;
    if (isC8Instance(upgradeExecutionDependencies)) {
      if (StringUtils.isBlank(upgradeExecutionDependencies.getConfigurationService().getOnboarding().getProperties().getClusterId())) {
        // self managed
        optimizeProfile = OptimizeProfile.CCSM;
      } else {
        //saas
        optimizeProfile = OptimizeProfile.CLOUD;
      }
    } else {
      // is c7
      optimizeProfile = OptimizeProfile.PLATFORM;
    }

    return new UpdateIndexStep(new MetadataIndexES(), addOptimizeProfileScript(optimizeProfile));
  }

  private static String addOptimizeProfileScript(OptimizeProfile optimizeProfile) {
    return String.format("ctx._source.optimizeProfile = '%s';\n", optimizeProfile.getId());
  }

  private boolean isC8Instance(final UpgradeExecutionDependencies upgradeExecutionDependencies) {
    return upgradeExecutionDependencies.getConfigurationService().getConfiguredZeebe().isEnabled()
      || isC8ImportDataPresent(upgradeExecutionDependencies);
  }

  private boolean isC8ImportDataPresent(final UpgradeExecutionDependencies upgradeExecutionDependencies) {
    CountRequest countRequest = new CountRequest().indices(POSITION_BASED_IMPORT_INDEX_NAME);

    CountResponse countResponse;
    try {
      countResponse = upgradeExecutionDependencies.getEsClient().count(countRequest);
    } catch (IOException e) {
      final String reason = "Was not able to determine existence of imported C8 data.";
      log.error(reason, e);
      throw new UpgradeRuntimeException(reason, e);
    }
    return countResponse.getCount() > 0;
  }

}
