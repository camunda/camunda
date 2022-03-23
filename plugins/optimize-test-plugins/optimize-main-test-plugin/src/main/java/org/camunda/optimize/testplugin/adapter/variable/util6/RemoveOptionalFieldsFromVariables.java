/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.testplugin.adapter.variable.util6;

import org.camunda.optimize.plugin.importing.variable.PluginVariableDto;
import org.camunda.optimize.plugin.importing.variable.VariableImportAdapter;

import java.util.List;
import java.util.stream.Collectors;

public class RemoveOptionalFieldsFromVariables implements VariableImportAdapter {

  @Override
  public List<PluginVariableDto> adaptVariables(List<PluginVariableDto> list) {
    return list.stream()
      .peek(pluginVariableDto -> {
        pluginVariableDto.setValue(null);
        pluginVariableDto.setValueInfo(null);
        pluginVariableDto.setTenantId(null);
      })
      .collect(Collectors.toList());
  }
}
