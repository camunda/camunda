/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.plan.factories;

import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_INDEX_PREFIX;

import io.camunda.optimize.service.db.schema.IndexMappingCreator;
import io.camunda.optimize.service.db.schema.index.IndexMappingCreatorBuilder;
import io.camunda.optimize.service.util.configuration.DatabaseType;
import io.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import io.camunda.optimize.upgrade.plan.UpgradeExecutionDependencies;
import io.camunda.optimize.upgrade.plan.UpgradePlan;
import io.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import io.camunda.optimize.upgrade.steps.schema.UpdateIndexStep;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class Upgrade89to810PlanFactory implements UpgradePlanFactory {

  @Override
  public UpgradePlan createUpgradePlan(final UpgradeExecutionDependencies dependencies) {
    final String prefix = dependencies.indexNameService().getIndexPrefix();
    final String fullPrefix = prefix + "-" + PROCESS_INSTANCE_INDEX_PREFIX;

    final Map<String, Set<String>> aliasMap;
    try {
      aliasMap = dependencies.databaseClient().getAliasesForIndexPattern(fullPrefix + "*");
    } catch (final Exception e) {
      throw new UpgradeRuntimeException(
          "Failed to enumerate process instance indices for upgrade", e);
    }

    final Function<String, IndexMappingCreator<?>> indexFactory;
    if (dependencies.databaseType() == DatabaseType.ELASTICSEARCH) {
      indexFactory = IndexMappingCreatorBuilder.PROCESS_INSTANCE_INDEX.getElasticsearch()::apply;
    } else {
      indexFactory = IndexMappingCreatorBuilder.PROCESS_INSTANCE_INDEX.getOpensearch()::apply;
    }

    final List<UpdateIndexStep> steps =
        aliasMap.entrySet().stream()
            .flatMap(entry -> entry.getValue().stream())
            .filter(alias -> alias.startsWith(fullPrefix))
            .map(alias -> alias.substring(fullPrefix.length()))
            .distinct()
            .map(key -> new UpdateIndexStep(indexFactory.apply(key)))
            .toList();

    return UpgradePlanBuilder.createUpgradePlan()
        .fromVersion("8.9")
        .toVersion("8.10.0")
        .addUpgradeSteps(steps)
        .build();
  }
}
