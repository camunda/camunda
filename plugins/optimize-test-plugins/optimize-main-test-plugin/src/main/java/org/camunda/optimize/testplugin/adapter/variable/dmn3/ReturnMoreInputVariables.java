/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.testplugin.adapter.variable.dmn3;

import org.camunda.optimize.plugin.importing.variable.DecisionInputImportAdapter;
import org.camunda.optimize.plugin.importing.variable.PluginDecisionInputDto;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ReturnMoreInputVariables implements DecisionInputImportAdapter {
  public List<PluginDecisionInputDto> adaptInputs(List<PluginDecisionInputDto> inputs) {
    List<PluginDecisionInputDto> newInputs = new ArrayList<>();
    for (PluginDecisionInputDto input : inputs) {
      PluginDecisionInputDto newInput = new PluginDecisionInputDto(
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
        input.getTenantId()
      );
      newInputs.add(newInput);
    }
    inputs.addAll(newInputs);
    return inputs;
  }
}

