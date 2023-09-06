/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.queries;

enum Operator {
  eq
}

public class TaskByVariables {
  private String name;
  private String value;
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
