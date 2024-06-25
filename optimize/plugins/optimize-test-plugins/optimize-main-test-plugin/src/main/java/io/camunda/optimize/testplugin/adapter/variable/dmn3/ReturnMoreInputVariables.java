/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.testplugin.adapter.variable.dmn3;

import io.camunda.optimize.plugin.importing.variable.DecisionInputImportAdapter;
import io.camunda.optimize.plugin.importing.variable.PluginDecisionInputDto;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ReturnMoreInputVariables implements DecisionInputImportAdapter {

  @Override
  public List<PluginDecisionInputDto> adaptInputs(final List<PluginDecisionInputDto> inputs) {
    final List<PluginDecisionInputDto> newInputs = new ArrayList<>();
    for (final PluginDecisionInputDto input : inputs) {
      final PluginDecisionInputDto newInput =
          new PluginDecisionInputDto(
              UUID.randomUUID().toString(),
              UUID.randomUUID().toString(),
              "foo",
              input.getType(),
              input.getValue(),
              input.getDecisionDefinitionKey(),
              input.getDecisionDefinitionVersion(),
              input.getDecisionDefinitionId(),
              input.getDecisionInstanceId(),
              input.getEngineAlias(),
              input.getTenantId());
      newInputs.add(newInput);
    }
    inputs.addAll(newInputs);
    return inputs;
  }
}
