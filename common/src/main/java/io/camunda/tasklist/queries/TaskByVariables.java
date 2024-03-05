/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.queries;

import io.swagger.v3.oas.annotations.media.Schema;

enum Operator {
  eq
}

public class TaskByVariables {
  @Schema(description = "The name of the variable.")
  private String name;

  @Schema(
      description =
          "The value of the variable. When specifying the variable value, it's crucial to maintain consistency with JSON values (serialization for the complex objects such as list) and ensure that strings remain appropriately formatted.")
  private String value;

  @Schema(description = "The comparison operator to use for the variable.<br>" + "* `eq`: Equals")
  private Operator operator;

  public String getName() {
    return name;
  }

  public TaskByVariables setName(String name) {
    this.name = name;
    return this;
  }

  public String getValue() {
    return value;
  }

  public TaskByVariables setValue(String value) {
    this.value = value;
    return this;
  }

  public Operator getOperator() {
    return operator;
  }

  public TaskByVariables setOperator(String operator) {
    this.operator = Operator.valueOf(operator);
    return this;
  }
}
