/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.testplugin.pluginloading.independent.testotherplugins;

import org.camunda.optimize.plugin.importing.variable.PluginVariableDto;
import org.camunda.optimize.plugin.importing.variable.VariableImportAdapter;
import org.camunda.optimize.testplugin.pluginloading.IndependentNewVariableDto;

import java.util.Collections;
import java.util.List;

public class IndependentTestPlugin implements VariableImportAdapter {

  @Override
  public List<PluginVariableDto> adaptVariables(final List<PluginVariableDto> variables) {
    final IndependentNewVariableDto newVariableDto = new IndependentNewVariableDto();

    if (newVariableDto.isThisMyPluginClassInstance()) {
      return Collections.singletonList(newVariableDto);
    } else {
      return Collections.emptyList();
    }
  }

}
