/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.testplugin.adapter.variable.util6;

import io.camunda.optimize.plugin.importing.variable.PluginVariableDto;
import io.camunda.optimize.plugin.importing.variable.VariableImportAdapter;
import java.util.List;
import java.util.stream.Collectors;

public class RemoveOptionalFieldsFromVariables implements VariableImportAdapter {

  @Override
  public List<PluginVariableDto> adaptVariables(List<PluginVariableDto> list) {
    return list.stream()
        .peek(
            pluginVariableDto -> {
              pluginVariableDto.setValue(null);
              pluginVariableDto.setValueInfo(null);
              pluginVariableDto.setTenantId(null);
            })
        .collect(Collectors.toList());
  }
}
