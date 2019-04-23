/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.result.raw;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.Map;

@Getter
@Setter
public class RawDataProcessInstanceDto {

  protected String processDefinitionKey;
  protected String processDefinitionId;
  protected String processInstanceId;
  protected String businessKey;
  protected OffsetDateTime startDate;
  protected OffsetDateTime endDate;
  protected String engineName;
  protected Map<String, Object> variables;

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof RawDataProcessInstanceDto) {
      RawDataProcessInstanceDto other = (RawDataProcessInstanceDto) obj;
      boolean result = processDefinitionId.equals(other.processDefinitionId);
      result = result && processDefinitionKey.equals(other.processDefinitionKey);
      result = result && processInstanceId.equals(other.processInstanceId);
      result = result && startDate.equals(other.startDate);
      result = result && endDate.equals(other.endDate);
      result = result && engineName.equals(other.engineName);
      result = result && businessKey.equals(other.businessKey);
      Map<String, Object> otherVariables = other.variables;
      for (Map.Entry<String, Object> nameToValue : variables.entrySet()) {
        result = result && otherVariables.containsKey(nameToValue.getKey());
        if (otherVariables.containsKey(nameToValue.getKey())) {
          if (otherVariables.get(nameToValue.getKey()) == null) {
            result = result && nameToValue.getValue() == null;
          } else {
            result = result && otherVariables.get(nameToValue.getKey()).equals(nameToValue.getValue());
          }
        }

      }
      return result;
    }
    return false;
  }
}
