/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.data.generation.generators.client.dto;

import java.util.HashMap;
import java.util.Map;

public class MessageCorrelationDto {

  String messageName;
  boolean all;
  Map<String, VariableValue> processVariables = new HashMap<>();

  public Map<String, VariableValue> getProcessVariables() {
    return processVariables;
  }

  public void setProcessVariables(Map<String, VariableValue> processVariables) {
    this.processVariables = processVariables;
  }

  public String getMessageName() {
    return messageName;
  }

  public void setMessageName(String messageName) {
    this.messageName = messageName;
  }

  public boolean isAll() {
    return all;
  }

  public void setAll(boolean all) {
    this.all = all;
  }
}
