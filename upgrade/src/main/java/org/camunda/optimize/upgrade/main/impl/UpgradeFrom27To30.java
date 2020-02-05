/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.main.impl;

import org.camunda.optimize.service.es.schema.index.VariableUpdateInstanceIndex;
import org.camunda.optimize.service.es.schema.index.events.EventIndex;
import org.camunda.optimize.service.es.schema.index.events.EventProcessMappingIndex;
import org.camunda.optimize.service.es.schema.index.events.EventSequenceCountIndex;
import org.camunda.optimize.upgrade.main.UpgradeProcedure;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.steps.schema.CreateIndexStep;
import org.camunda.optimize.upgrade.steps.schema.UpdateIndexStep;

public class UpgradeFrom27To30 extends UpgradeProcedure {
  private static final String FROM_VERSION = "2.7.0";
  private static final String TO_VERSION = "3.0.0";

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
      .addUpgradeStep(new UpdateIndexStep(new EventIndex(), null))
      .addUpgradeStep(new UpdateIndexStep(new EventSequenceCountIndex(), null))
      .addUpgradeStep(addEventSourcesAndRolesField())
      .addUpgradeStep(new CreateIndexStep(new VariableUpdateInstanceIndex()))
      .build();
  }

  private UpdateIndexStep addEventSourcesAndRolesField() {
    //@formatter:off
    final String script =
      "ctx._source.eventSources = new ArrayList();\n" +
       // initialize last role based on last modifier
      "Map identity = new HashMap();\n " +
      "String lastModifier = ctx._source.lastModifier;\n " +
      "identity.put(\"id\", lastModifier);\n " +
      "identity.put(\"type\", \"user\");\n " +
      "Map roleEntry = new HashMap();\n " +
      "roleEntry.put(\"id\", \"USER:\" + lastModifier);\n " +
      "roleEntry.put(\"identity\", identity);\n " +
      "ctx._source.roles = new ArrayList();\n " +
      "ctx._source.roles.add(roleEntry);\n";
    //@formatter:on

    return new UpdateIndexStep(
      new EventProcessMappingIndex(),
      script
    );
  }

}
