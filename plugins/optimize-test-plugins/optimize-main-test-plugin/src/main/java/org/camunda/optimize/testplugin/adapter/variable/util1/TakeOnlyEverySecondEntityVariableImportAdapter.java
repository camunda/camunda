/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.testplugin.adapter.variable.util1;


import org.camunda.optimize.plugin.importing.variable.PluginVariableDto;
import org.camunda.optimize.plugin.importing.variable.VariableImportAdapter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class TakeOnlyEverySecondEntityVariableImportAdapter implements VariableImportAdapter {

  @Override
  public List<PluginVariableDto> adaptVariables(List<PluginVariableDto> list) {
    int counter = 0;
    List<PluginVariableDto> newList = new ArrayList<>();
    // for consistent behavior
    final List<PluginVariableDto> sortedByName = list.stream()
      .sorted(Comparator.comparing(PluginVariableDto::getName))
      .collect(Collectors.toList());
    for (PluginVariableDto pluginVariableDto : sortedByName) {
      if (counter % 2 == 0) {
        newList.add(pluginVariableDto);
      }
      counter++;
    }
    return newList;
  }
}
