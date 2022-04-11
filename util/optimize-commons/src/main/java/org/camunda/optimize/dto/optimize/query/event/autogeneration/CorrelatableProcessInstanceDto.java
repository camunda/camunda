/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.event.autogeneration;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.camunda.optimize.dto.optimize.query.event.process.source.CamundaEventSourceEntryDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.EventSourceEntryDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.EventSourceType;
import org.camunda.optimize.dto.optimize.query.variable.SimpleProcessVariableDto;

import java.util.List;

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
  public String getCorrelationValueForEventSource(final EventSourceEntryDto<?> eventSourceEntryDto) {
    if (eventSourceEntryDto instanceof CamundaEventSourceEntryDto) {
      final CamundaEventSourceEntryDto camundaSource = (CamundaEventSourceEntryDto) eventSourceEntryDto;
      if (camundaSource.getConfiguration().isTracedByBusinessKey()) {
        return businessKey;
      } else {
        final String traceVariableName = camundaSource.getConfiguration().getTraceVariable();
        return variables
          .stream()
          .filter(var -> var.getName().equals(traceVariableName))
          .map(simpleVar -> simpleVar.getValue().stream().findFirst().orElse(null))
          .findFirst().orElse(null);
      }
    }
    throw new IllegalArgumentException("Cannot get correlation value from non-Camunda sources");
  }

}
