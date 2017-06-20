package org.camunda.optimize.plugin.adapter.variable.error1;


import org.camunda.optimize.plugin.importing.variable.PluginVariableDto;
import org.camunda.optimize.plugin.importing.variable.VariableImportAdapter;

import java.util.ArrayList;
import java.util.List;

public class NoDefaultConstructorVariableImportAdapter implements VariableImportAdapter {

  public NoDefaultConstructorVariableImportAdapter(String foo) {}

  @Override
  public List<PluginVariableDto> adaptVariables(List<PluginVariableDto> list) {
    return new ArrayList<>();
  }
}
