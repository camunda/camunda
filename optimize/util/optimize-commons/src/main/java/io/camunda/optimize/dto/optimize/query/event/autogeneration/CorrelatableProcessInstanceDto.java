/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.event.autogeneration;

import io.camunda.optimize.dto.optimize.query.event.process.source.CamundaEventSourceEntryDto;
import io.camunda.optimize.dto.optimize.query.event.process.source.EventSourceEntryDto;
import io.camunda.optimize.dto.optimize.query.event.process.source.EventSourceType;
import io.camunda.optimize.dto.optimize.query.variable.SimpleProcessVariableDto;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class CorrelatableProcessInstanceDto extends CorrelatableInstanceDto {
  private String processDefinitionKey;
  private String businessKey;
  private List<SimpleProcessVariableDto> variables;

  @Override
  public String getSourceIdentifier() {
    return EventSourceType.CAMUNDA.getId() + ":" + processDefinitionKey;
  }

  @Override
  public String getCorrelationValueForEventSource(
      final EventSourceEntryDto<?> eventSourceEntryDto) {
    if (eventSourceEntryDto instanceof CamundaEventSourceEntryDto) {
      final CamundaEventSourceEntryDto camundaSource =
          (CamundaEventSourceEntryDto) eventSourceEntryDto;
      if (camundaSource.getConfiguration().isTracedByBusinessKey()) {
        return businessKey;
      } else {
        final String traceVariableName = camundaSource.getConfiguration().getTraceVariable();
        return variables.stream()
            .filter(var -> var.getName().equals(traceVariableName))
            .map(simpleVar -> simpleVar.getValue().stream().findFirst().orElse(null))
            .findFirst()
            .orElse(null);
      }
    }
    throw new IllegalArgumentException("Cannot get correlation value from non-Camunda sources");
  }
}
