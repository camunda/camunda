package org.camunda.optimize.plugin.adapter.variable.util1;


import org.camunda.optimize.plugin.importing.variable.PluginVariableDto;
import org.camunda.optimize.plugin.importing.variable.VariableImportAdapter;

import java.util.ArrayList;
import java.util.List;

public class TakeOnlyEverySecondEntityVariableImportAdapter implements VariableImportAdapter {

  private int counter = 0;

  @Override
  public List<PluginVariableDto> adaptVariables(List<PluginVariableDto> list) {
    List<PluginVariableDto> newList = new ArrayList<>();
    for (PluginVariableDto pluginVariableDto : list) {
      if (counter % 2 == 0) {
        newList.add(pluginVariableDto);
      }
      counter++;
    }
    counter = 0;
    return newList;
  }
}
