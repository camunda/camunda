/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.importing;

import lombok.Getter;
import lombok.Setter;
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

@Getter
@Setter
public class ProcessInstanceDto implements OptimizeDto {

  protected String processDefinitionKey;
  protected String processDefinitionVersion;
  protected String processDefinitionId;
  protected String processInstanceId;
  protected String businessKey;
  protected OffsetDateTime startDate;
  protected OffsetDateTime endDate;
  protected Long durationInMs;
  protected String engine;
  protected String state;
  protected List<SimpleEventDto> events = new ArrayList<>();
  protected List<SimpleUserTaskInstanceDto> userTasks = new ArrayList<>();

  protected List<StringVariableDto> stringVariables = new ArrayList<>();
  protected List<IntegerVariableDto> integerVariables = new ArrayList<>();
  protected List<LongVariableDto> longVariables = new ArrayList<>();
  protected List<ShortVariableDto> shortVariables = new ArrayList<>();
  protected List<DoubleVariableDto> doubleVariables = new ArrayList<>();
  protected List<DateVariableDto> dateVariables = new ArrayList<>();
  protected List<BooleanVariableDto> booleanVariables = new ArrayList<>();

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
