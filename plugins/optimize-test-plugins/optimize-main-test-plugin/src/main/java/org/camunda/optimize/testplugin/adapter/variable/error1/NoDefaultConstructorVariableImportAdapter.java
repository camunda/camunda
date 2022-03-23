/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.testplugin.adapter.variable.error1;


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
