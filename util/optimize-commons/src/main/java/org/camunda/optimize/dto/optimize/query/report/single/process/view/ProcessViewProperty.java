/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.view;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.EqualsAndHashCode;
import org.camunda.optimize.dto.optimize.query.report.Combinable;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;

import static org.camunda.optimize.dto.optimize.ReportConstants.VIEW_DURATION_PROPERTY;
import static org.camunda.optimize.dto.optimize.ReportConstants.VIEW_FREQUENCY_PROPERTY;
import static org.camunda.optimize.dto.optimize.ReportConstants.VIEW_RAW_DATA_PROPERTY;

@EqualsAndHashCode
public class ProcessViewProperty implements Combinable {
  public static final ProcessViewProperty FREQUENCY =
    new ProcessViewProperty(VIEW_FREQUENCY_PROPERTY);
  public static final ProcessViewProperty DURATION =
    new ProcessViewProperty(VIEW_DURATION_PROPERTY);
  public static final ProcessViewProperty RAW_DATA =
    new ProcessViewProperty(VIEW_RAW_DATA_PROPERTY);

  public static ProcessViewProperty VARIABLE(final String variableName, final VariableType variableType) {
    return new ProcessViewProperty(variableName, variableType);
  }

  private final TypedViewPropertyDto viewPropertyDto;

  @JsonCreator
  public ProcessViewProperty(final String singleStringProperty) {
    this.viewPropertyDto = new StringViewPropertyDto(singleStringProperty);
  }

  @JsonCreator
  private ProcessViewProperty(@JsonProperty("name")final String name, @JsonProperty("type")final VariableType type) {
    this.viewPropertyDto = new VariableViewPropertyDto(name, type);
  }

  @Override
  public boolean isCombinable(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ProcessViewProperty)) {
      return false;
    }
    ProcessViewProperty other = (ProcessViewProperty) o;
    return Combinable.isCombinable(viewPropertyDto, other.viewPropertyDto);
  }

  @JsonValue
  public Object getViewPropertyDto() {
    return viewPropertyDto;
  }

  @Override
  public String toString() {
    return viewPropertyDto.toString();
  }
}
