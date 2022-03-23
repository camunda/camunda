/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.testplugin.adapter.variable.dmn2;

import org.camunda.optimize.plugin.importing.variable.DecisionInputImportAdapter;
import org.camunda.optimize.plugin.importing.variable.PluginDecisionInputDto;

import java.util.List;

public class ReturnInvalidInputs implements DecisionInputImportAdapter {

  @Override
  public List<PluginDecisionInputDto> adaptInputs(List<PluginDecisionInputDto> inputs) {
    for (PluginDecisionInputDto input : inputs) {
      input.setType(null);
    }
    return inputs;
  }
}