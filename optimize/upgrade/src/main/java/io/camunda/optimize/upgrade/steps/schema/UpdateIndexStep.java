/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.steps.schema;

import io.camunda.optimize.service.db.schema.IndexMappingCreator;
import io.camunda.optimize.upgrade.db.SchemaUpgradeClient;
import io.camunda.optimize.upgrade.steps.UpgradeStep;
import io.camunda.optimize.upgrade.steps.UpgradeStepType;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class UpdateIndexStep extends UpgradeStep {

  private final String mappingScript;
  private final Map<String, Object> parameters;
  private final Set<String> additionalReadAliases;

  public UpdateIndexStep(final IndexMappingCreator index) {
    this(index, null, Collections.emptyMap(), Collections.emptySet());
  }

  public UpdateIndexStep(final IndexMappingCreator index, final Set<String> additionalReadAliases) {
    this(index, null, Collections.emptyMap(), additionalReadAliases);
  }

  public UpdateIndexStep(final IndexMappingCreator index, final String mappingScript) {
    this(index, mappingScript, Collections.emptyMap(), Collections.emptySet());
  }

  public UpdateIndexStep(
      final IndexMappingCreator index,
      final String mappingScript,
      final Map<String, Object> parameters,
      final Set<String> additionalReadAliases) {
    super(index);
    this.mappingScript = mappingScript;
    this.parameters = parameters;
    this.additionalReadAliases = additionalReadAliases;
  }

  @Override
  public UpgradeStepType getType() {
    return UpgradeStepType.SCHEMA_UPDATE_INDEX;
  }

  @Override
  public void performUpgradeStep(final SchemaUpgradeClient<?, ?> schemaUpgradeClient) {
    schemaUpgradeClient.updateIndex(index, mappingScript, parameters, additionalReadAliases);
  }
}
