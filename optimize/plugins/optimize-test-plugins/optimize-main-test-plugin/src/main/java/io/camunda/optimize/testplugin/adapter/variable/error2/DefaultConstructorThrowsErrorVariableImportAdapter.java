/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.testplugin.adapter.variable.error2;

import io.camunda.optimize.plugin.importing.variable.PluginVariableDto;
import io.camunda.optimize.plugin.importing.variable.VariableImportAdapter;
import java.util.ArrayList;
import java.util.List;

public class DefaultConstructorThrowsErrorVariableImportAdapter implements VariableImportAdapter {

  public DefaultConstructorThrowsErrorVariableImportAdapter() {
    throw new RuntimeException();
  }

  @Override
  public List<PluginVariableDto> adaptVariables(List<PluginVariableDto> list) {
    return new ArrayList<>();
  }
}
