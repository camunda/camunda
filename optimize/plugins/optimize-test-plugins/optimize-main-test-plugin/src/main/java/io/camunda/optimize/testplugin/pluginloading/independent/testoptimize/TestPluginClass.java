/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
