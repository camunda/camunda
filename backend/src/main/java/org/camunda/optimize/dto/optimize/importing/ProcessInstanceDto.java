/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.importing;

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

  public String getProcessDefinitionVersion() {
    return processDefinitionVersion;
  }

  public void setProcessDefinitionVersion(String processDefinitionVersion) {
    this.processDefinitionVersion = processDefinitionVersion;
  }

  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public void setProcessDefinitionKey(String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public void setProcessDefinitionId(String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
  }

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public void setProcessInstanceId(String processInstanceId) {
    this.processInstanceId = processInstanceId;
  }

  public String getBusinessKey() {
    return businessKey;
  }

  public void setBusinessKey(String businessKey) {
    this.businessKey = businessKey;
  }

  public OffsetDateTime getStartDate() {
    return startDate;
  }

  public void setStartDate(OffsetDateTime startDate) {
    this.startDate = startDate;
  }

  public OffsetDateTime getEndDate() {
    return endDate;
  }

  public void setEndDate(OffsetDateTime endDate) {
    this.endDate = endDate;
  }

  public Long getDurationInMs() {
    return durationInMs;
  }

  public void setDurationInMs(Long durationInMs) {
    this.durationInMs = durationInMs;
  }

  public List<SimpleEventDto> getEvents() {
    return events;
  }

  public void setEvents(List<SimpleEventDto> events) {
    this.events = events;
  }

  public List<StringVariableDto> getStringVariables() {
    return stringVariables;
  }

  public void setStringVariables(List<StringVariableDto> stringVariables) {
    this.stringVariables = stringVariables;
  }

  public List<BooleanVariableDto> getBooleanVariables() {
    return booleanVariables;
  }

  public void setBooleanVariables(List<BooleanVariableDto> booleanVariables) {
    this.booleanVariables = booleanVariables;
  }

  public List<IntegerVariableDto> getIntegerVariables() {
    return integerVariables;
  }

  public void setIntegerVariables(List<IntegerVariableDto> integerVariables) {
    this.integerVariables = integerVariables;
  }

  public List<LongVariableDto> getLongVariables() {
    return longVariables;
  }

  public void setLongVariables(List<LongVariableDto> longVariables) {
    this.longVariables = longVariables;
  }

  public List<ShortVariableDto> getShortVariables() {
    return shortVariables;
  }

  public void setShortVariables(List<ShortVariableDto> shortVariables) {
    this.shortVariables = shortVariables;
  }

  public List<DoubleVariableDto> getDoubleVariables() {
    return doubleVariables;
  }

  public void setDoubleVariables(List<DoubleVariableDto> doubleVariables) {
    this.doubleVariables = doubleVariables;
  }

  public List<DateVariableDto> getDateVariables() {
    return dateVariables;
  }

  public void setDateVariables(List<DateVariableDto> dateVariables) {
    this.dateVariables = dateVariables;
  }

  public String getEngine() {
    return engine;
  }

  public void setEngine(String engine) {
    this.engine = engine;
  }

  public String getState() { return state; }

  public void setState(String state) { this.state = state; }

  public List<SimpleUserTaskInstanceDto> getUserTasks() {
    return userTasks;
  }
}
