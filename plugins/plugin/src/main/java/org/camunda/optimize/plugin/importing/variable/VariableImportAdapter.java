/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.plugin.importing.variable;

import java.util.List;

public interface VariableImportAdapter {

  /**
   * Adapts the list of variables to be imported by adding new entities or filter variables
   * that should not be analyzed by Optimize.
   *
   * @param variables The variables that would be imported by Optimize, which represent variables from the engine.
   * @return An adapted list that is imported to Optimize.
   */
  List<PluginVariableDto> adaptVariables(List<PluginVariableDto> variables);
}
