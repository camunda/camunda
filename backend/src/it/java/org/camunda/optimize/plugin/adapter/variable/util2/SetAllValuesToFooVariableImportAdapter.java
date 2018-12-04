package org.camunda.optimize.plugin.adapter.variable.util2;


import org.camunda.optimize.plugin.importing.variable.PluginVariableDto;
import org.camunda.optimize.plugin.importing.variable.VariableImportAdapter;

import java.util.List;

public class SetAllValuesToFooVariableImportAdapter implements VariableImportAdapter {

  @Override
  public List<PluginVariableDto> adaptVariables(List<PluginVariableDto> list) {
    for (PluginVariableDto pluginVariableDto : list) {
      pluginVariableDto.setValue("foo");
    }
    return list;
  }
}
