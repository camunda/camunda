/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.EqualsAndHashCode;
import org.camunda.optimize.dto.optimize.query.report.Combinable;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.StringViewPropertyDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.TypedViewPropertyDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.VariableViewPropertyDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;

import java.util.Optional;

import static org.camunda.optimize.dto.optimize.ReportConstants.VIEW_DURATION_PROPERTY;
import static org.camunda.optimize.dto.optimize.ReportConstants.VIEW_FREQUENCY_PROPERTY;
import static org.camunda.optimize.dto.optimize.ReportConstants.VIEW_PERCENTAGE_PROPERTY;
import static org.camunda.optimize.dto.optimize.ReportConstants.VIEW_RAW_DATA_PROPERTY;

@EqualsAndHashCode
public class ViewProperty implements Combinable {
  public static final ViewProperty FREQUENCY = new ViewProperty(VIEW_FREQUENCY_PROPERTY);
  public static final ViewProperty DURATION = new ViewProperty(VIEW_DURATION_PROPERTY);
  public static final ViewProperty PERCENTAGE = new ViewProperty(VIEW_PERCENTAGE_PROPERTY);
  public static final ViewProperty RAW_DATA = new ViewProperty(VIEW_RAW_DATA_PROPERTY);

  // uppercase is intended here to align it with other static fields
  @SuppressWarnings("java:S100")
  public static ViewProperty VARIABLE(final String variableName, final VariableType variableType) {
    return new ViewProperty(variableName, variableType);
  }

  private final TypedViewPropertyDto viewPropertyDto;

  @JsonCreator
  public ViewProperty(final String singleStringProperty) {
    this.viewPropertyDto = new StringViewPropertyDto(singleStringProperty);
  }

  @JsonCreator
  private ViewProperty(@JsonProperty("name") final String name, @JsonProperty("type") final VariableType type) {
    this.viewPropertyDto = new VariableViewPropertyDto(name, type);
  }

  @Override
  public boolean isCombinable(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ViewProperty)) {
      return false;
    }
    ViewProperty other = (ViewProperty) o;
    return Combinable.isCombinable(viewPropertyDto, other.viewPropertyDto);
  }

  @JsonValue
  public TypedViewPropertyDto getViewPropertyDto() {
    return viewPropertyDto;
  }

  @JsonIgnore
  public <T extends TypedViewPropertyDto> Optional<T> getViewPropertyDtoIfOfType(final Class<T> clazz) {
    return Optional.of(this.viewPropertyDto)
      .filter(clazz::isInstance)
      .map(clazz::cast);
  }

  @Override
  public String toString() {
    return viewPropertyDto.toString();
  }
}
