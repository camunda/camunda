/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.importing;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.dto.optimize.query.variable.value.BooleanVariableDto;
import org.camunda.optimize.dto.optimize.query.variable.value.DateVariableDto;
import org.camunda.optimize.dto.optimize.query.variable.value.DoubleVariableDto;
import org.camunda.optimize.dto.optimize.query.variable.value.IntegerVariableDto;
import org.camunda.optimize.dto.optimize.query.variable.value.LongVariableDto;
import org.camunda.optimize.dto.optimize.query.variable.value.ShortVariableDto;
import org.camunda.optimize.dto.optimize.query.variable.value.StringVariableDto;
import org.camunda.optimize.dto.optimize.query.variable.value.VariableInstanceDto;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Data
public class ProcessInstanceDto implements OptimizeDto {

  private String processDefinitionKey;
  private String processDefinitionVersion;
  private String processDefinitionId;
  private String processInstanceId;
  private String businessKey;
  private OffsetDateTime startDate;
  private OffsetDateTime endDate;
  private Long durationInMs;
  private String state;
  private List<SimpleEventDto> events = new ArrayList<>();
  private List<UserTaskInstanceDto> userTasks = new ArrayList<>();
  private List<StringVariableDto> stringVariables = new ArrayList<>();
  private List<IntegerVariableDto> integerVariables = new ArrayList<>();
  private List<LongVariableDto> longVariables = new ArrayList<>();
  private List<ShortVariableDto> shortVariables = new ArrayList<>();
  private List<DoubleVariableDto> doubleVariables = new ArrayList<>();
  private List<DateVariableDto> dateVariables = new ArrayList<>();
  private List<BooleanVariableDto> booleanVariables = new ArrayList<>();
  private String engine;
  private String tenantId;

  public ProcessInstanceDto(final String processDefinitionKey, final String processDefinitionVersion,
                            final String processDefinitionId, final String processInstanceId,
                            final String businessKey, final OffsetDateTime startDate, final OffsetDateTime endDate,
                            final Long durationInMs, final String state, final String engine, final String tenantId) {
    this.processDefinitionKey = processDefinitionKey;
    this.processDefinitionVersion = processDefinitionVersion;
    this.processDefinitionId = processDefinitionId;
    this.processInstanceId = processInstanceId;
    this.businessKey = businessKey;
    this.startDate = startDate;
    this.endDate = endDate;
    this.durationInMs = durationInMs;
    this.state = state;
    this.engine = engine;
    this.tenantId = tenantId;
  }

  public void addVariableInstance(VariableInstanceDto variableInstanceDto) {
    if (variableInstanceDto instanceof StringVariableDto) {
      StringVariableDto stringInstance = (StringVariableDto) variableInstanceDto;
      stringVariables.add(stringInstance);
    } else if (variableInstanceDto instanceof IntegerVariableDto) {
      IntegerVariableDto integerVariableDto = (IntegerVariableDto) variableInstanceDto;
      integerVariables.add(integerVariableDto);
    } else if (variableInstanceDto instanceof LongVariableDto) {
      LongVariableDto longVariableDto = (LongVariableDto) variableInstanceDto;
      longVariables.add(longVariableDto);
    } else if (variableInstanceDto instanceof ShortVariableDto) {
      ShortVariableDto shortVariableDto = (ShortVariableDto) variableInstanceDto;
      shortVariables.add(shortVariableDto);
    } else if (variableInstanceDto instanceof DoubleVariableDto) {
      DoubleVariableDto doubleVariableDto = (DoubleVariableDto) variableInstanceDto;
      doubleVariables.add(doubleVariableDto);
    } else if (variableInstanceDto instanceof BooleanVariableDto) {
      BooleanVariableDto booleanVariableDto = (BooleanVariableDto) variableInstanceDto;
      booleanVariables.add(booleanVariableDto);
    } else if (variableInstanceDto instanceof DateVariableDto) {
      DateVariableDto dateVariableDto = (DateVariableDto) variableInstanceDto;
      dateVariables.add(dateVariableDto);
    }
  }

  public List<VariableInstanceDto> obtainAllVariables() {
    List<VariableInstanceDto> variables = new ArrayList<>();
    variables.addAll(stringVariables);
    variables.addAll(integerVariables);
    variables.addAll(longVariables);
    variables.addAll(shortVariables);
    variables.addAll(doubleVariables);
    variables.addAll(dateVariables);
    variables.addAll(booleanVariables);
    return variables;
  }

}
