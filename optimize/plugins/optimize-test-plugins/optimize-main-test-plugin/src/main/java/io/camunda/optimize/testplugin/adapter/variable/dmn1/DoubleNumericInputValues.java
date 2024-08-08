/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.testplugin.adapter.variable.dmn1;

import io.camunda.optimize.plugin.importing.variable.DecisionInputImportAdapter;
import io.camunda.optimize.plugin.importing.variable.PluginDecisionInputDto;
import java.util.List;

public class DoubleNumericInputValues implements DecisionInputImportAdapter {

  @Override
  public List<PluginDecisionInputDto> adaptInputs(final List<PluginDecisionInputDto> inputs) {
    for (final PluginDecisionInputDto input : inputs) {
      if (input.getType().equalsIgnoreCase("double")) {
        input.setValue(String.valueOf(Double.parseDouble(input.getValue()) * 2));
      }
    }
    return inputs;
  }
}
