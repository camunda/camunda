/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.steps.schema;

import lombok.EqualsAndHashCode;
import org.camunda.optimize.service.es.schema.IndexMappingCreator;
import org.camunda.optimize.upgrade.es.SchemaUpgradeClient;
import org.camunda.optimize.upgrade.steps.UpgradeStep;
import org.camunda.optimize.upgrade.steps.UpgradeStepType;

import java.util.Set;

import static java.util.stream.Collectors.toSet;
import static org.camunda.optimize.upgrade.steps.UpgradeStepType.ADD_ALIAS;

@EqualsAndHashCode(callSuper = true)
public class AddAliasStep extends UpgradeStep {
  private final boolean isWriteAlias;
  private final Set<String> indexAliasesWithoutPrefix;

  public AddAliasStep(final IndexMappingCreator index, final boolean isWriteAlias,
                      final Set<String> indexAliasesWithoutPrefix) {
    super(index);
    this.isWriteAlias = isWriteAlias;
    this.indexAliasesWithoutPrefix = indexAliasesWithoutPrefix;
  }

  @Override
  public UpgradeStepType getType() {
    return ADD_ALIAS;
  }

  @Override
  public void execute(final SchemaUpgradeClient schemaUpgradeClient) {
    Set<String> indexAliasesWithPrefix = indexAliasesWithoutPrefix.stream()
      .map(alias -> schemaUpgradeClient.getIndexNameService().getOptimizeIndexAliasForIndex(alias))
      .collect(toSet());

    schemaUpgradeClient.addAliases(
      indexAliasesWithPrefix,
      schemaUpgradeClient.getIndexNameService().getOptimizeIndexNameWithVersionWithoutSuffix(index),
      isWriteAlias
    );
  }
}
