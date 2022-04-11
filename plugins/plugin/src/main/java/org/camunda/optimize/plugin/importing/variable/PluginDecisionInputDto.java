/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.plugin.importing.variable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class PluginDecisionInputDto {

  /**
   * Unique id of the decision input value.
   */
  private String id;

  /**
   * The id of the clause the input value belongs to.
   */
  private String clauseId;

  /**
   * The name of the clause the input value belongs to.
   */
  private String clauseName;

  /**
   * The value type of the variable.
   * Only simple variable types (i.e. Boolean, String, Date, Long, Short, Integer, Double) can be imported to optimize.
   * Make sure to set the variable type to one of these values while adapting the inputs
   *
   * Note: This field is required in order to be imported to Optimize.
   */
  private String type;

  /**
   * The string representation of the input variable's value.
   */
  private String value;

  /**
   * The key of the decision definition, current input corresponds to.
   */
  private String decisionDefinitionKey;

  /**
   * The version of the decision definition, current input corresponds to.
   */
  private String decisionDefinitionVersion;

  /**
   * The ID of the decision definition, current input corresponds to.
   */
  private String decisionDefinitionId;

  /**
   * The ID of the decision instance, current input corresponds to.
   */
  private String decisionInstanceId;

  /**
   * Alias of the engine, from which the current Decision Instance is imported.
   */
  private String engineAlias;

  /**
   * The field states the tenant this variable instance belongs to.
   * <p>
   * Note: Might be null if no tenant is assigned.
   */
  private String tenantId;

}
