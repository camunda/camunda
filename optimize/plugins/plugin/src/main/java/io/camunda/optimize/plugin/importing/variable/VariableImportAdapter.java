/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.plugin.importing.variable;

import java.util.List;

public interface VariableImportAdapter {

  /**
   * Adapts the list of variables to be imported by adding new entities or filter variables that
   * should not be analyzed by Optimize.
   *
   * @param variables The variables that would be imported by Optimize, which represent variables
   * from the engine.
   * @return An adapted list that is imported to Optimize.
   */
  List<PluginVariableDto> adaptVariables(List<PluginVariableDto> variables);
}
