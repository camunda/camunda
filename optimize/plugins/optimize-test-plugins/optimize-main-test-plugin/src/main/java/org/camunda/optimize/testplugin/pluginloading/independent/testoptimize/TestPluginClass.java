/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.testplugin.pluginloading.independent.testoptimize;

import io.camunda.optimize.plugin.importing.variable.PluginVariableDto;
import io.camunda.optimize.plugin.importing.variable.VariableImportAdapter;
import io.camunda.optimize.testplugin.pluginloading.IndependentNewVariableDto;
import io.camunda.optimize.testplugin.pluginloading.SharedTestPluginVariableDto;
import java.util.Arrays;
import java.util.List;

public class TestPluginClass implements VariableImportAdapter {

  @Override
  public List<PluginVariableDto> adaptVariables(final List<PluginVariableDto> variables) {
    final List<PluginVariableDto> pluginVariableDtos =
        Arrays.asList(new SharedTestPluginVariableDto(), new IndependentNewVariableDto());

    return pluginVariableDtos;
  }
}
