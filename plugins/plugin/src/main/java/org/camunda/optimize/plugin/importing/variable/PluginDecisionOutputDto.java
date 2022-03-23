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
public class PluginDecisionOutputDto {

  /**
   * The id of the decision output value.
   */
  private String id;

  /**
   * The id of the clause the output value belongs to.
   */
  private String clauseId;

  /**
   * The name of the clause the output value belongs to.
   */
  private String clauseName;

  /**
   * The id of the rule the output value belongs to.
   */
  private String ruleId;

  /**
   * The order of the rule the output value belongs to.
   */
  private Integer ruleOrder;

  /**
   * The name of the output variable.
   */
  private String variableName;

  /**
   * The value type of the variable.
   * Only simple variable types (i.e. Boolean, String, Date, Long, Short, Integer, Double) can be imported to optimize.
   * Make sure to set the variable type to one of these values while adapting the outputs.
   *
   * Note: This field is required in order to be imported to Optimize.
   */
  private String type;

  /**
   * A string representation of the output variable's value.
   */
  private String value;

  /**
   * The key of the decision definition the current output corresponds to.
   */
  private String decisionDefinitionKey;

  /**
   * The version of the decision definition the current output corresponds to.
   */
  private String decisionDefinitionVersion;

  /**
   * The ID of the decision definition the current output corresponds to.
   */
  private String decisionDefinitionId;


  /**
   * The ID of the decision instance the current output corresponds to.
   */
  private String decisionInstanceId;

  /**
   * Alias of the engine from which the current Decision Instance is imported.
   */
  private String engineAlias;

  /**
   * The field states the tenant this variable instance belongs to.
   *
   * Note: Might be null if no tenant is assigned.
   */
  private String tenantId;

}