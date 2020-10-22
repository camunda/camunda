/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.main.impl;

import org.camunda.optimize.upgrade.main.UpgradeProcedure;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.steps.UpgradeStep;
import org.camunda.optimize.upgrade.steps.document.UpdateDataStep;

import java.util.Arrays;
import java.util.List;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_PROCESS_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_DEFINITION_INDEX_NAME;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;

public class UpgradeFrom32To33 extends UpgradeProcedure {

  public static final String FROM_VERSION = "3.2.0";
  public static final String TO_VERSION = "3.3.0";

  @Override
  public String getInitialVersion() {
    return FROM_VERSION;
  }

  @Override
  public String getTargetVersion() {
    return TO_VERSION;
  }

  public UpgradePlan buildUpgradePlan() {
    return UpgradePlanBuilder.createUpgradePlan()
      .addUpgradeDependencies(upgradeDependencies)
      .fromVersion(FROM_VERSION)
      .toVersion(TO_VERSION)
      .addUpgradeSteps(markExistingDefinitionsAsNotDeleted())
      .build();
  }

  private List<UpgradeStep> markExistingDefinitionsAsNotDeleted() {
    final String script = "ctx._source.deleted = false;";
    return Arrays.asList(
      new UpdateDataStep(PROCESS_DEFINITION_INDEX_NAME, matchAllQuery(), script),
      new UpdateDataStep(DECISION_DEFINITION_INDEX_NAME, matchAllQuery(), script),
      new UpdateDataStep(EVENT_PROCESS_DEFINITION_INDEX_NAME, matchAllQuery(), script)
    );
  }

}
