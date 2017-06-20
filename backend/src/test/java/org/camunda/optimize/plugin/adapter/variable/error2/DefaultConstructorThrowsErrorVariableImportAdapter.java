package org.camunda.optimize.plugin.adapter.variable.error2;

import org.camunda.optimize.plugin.importing.variable.PluginVariableDto;
import org.camunda.optimize.plugin.importing.variable.VariableImportAdapter;
import org.camunda.optimize.service.exceptions.OptimizeException;

import java.util.ArrayList;
import java.util.List;

public class DefaultConstructorThrowsErrorVariableImportAdapter implements VariableImportAdapter {

  public DefaultConstructorThrowsErrorVariableImportAdapter() throws OptimizeException {
    throw new OptimizeException();
  }

  @Override
  public List<PluginVariableDto> adaptVariables(List<PluginVariableDto> list) {
    return new ArrayList<>();
  }
}
