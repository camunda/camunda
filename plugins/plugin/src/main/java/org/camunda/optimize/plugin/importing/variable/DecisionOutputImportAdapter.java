/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.plugin.importing.variable;

import java.util.List;

public interface DecisionOutputImportAdapter {

  /**
   * Adapts the list of Decision outputs to be imported by adding new entities or filter variables
   * that should not be analyzed by Optimize.
   *
   * @param outputs The decision output instances that would be imported by Optimize, which represent instances from the engine.
   * @return An adapted list that is imported to Optimize.
   */
  List<PluginDecisionOutputDto> adaptOutputs(List<PluginDecisionOutputDto> outputs);
}
