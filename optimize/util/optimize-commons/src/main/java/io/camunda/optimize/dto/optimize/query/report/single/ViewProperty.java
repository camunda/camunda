/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single;

import static io.camunda.optimize.dto.optimize.ReportConstants.VIEW_DURATION_PROPERTY;
import static io.camunda.optimize.dto.optimize.ReportConstants.VIEW_FREQUENCY_PROPERTY;
import static io.camunda.optimize.dto.optimize.ReportConstants.VIEW_PERCENTAGE_PROPERTY;
import static io.camunda.optimize.dto.optimize.ReportConstants.VIEW_RAW_DATA_PROPERTY;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import io.camunda.optimize.dto.optimize.query.report.Combinable;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.StringViewPropertyDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.TypedViewPropertyDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.VariableViewPropertyDto;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import java.util.Optional;

public class ViewProperty implements Combinable {

  public static final ViewProperty FREQUENCY = new ViewProperty(VIEW_FREQUENCY_PROPERTY);
  public static final ViewProperty DURATION = new ViewProperty(VIEW_DURATION_PROPERTY);
  public static final ViewProperty PERCENTAGE = new ViewProperty(VIEW_PERCENTAGE_PROPERTY);
  public static final ViewProperty RAW_DATA = new ViewProperty(VIEW_RAW_DATA_PROPERTY);
  private final TypedViewPropertyDto viewPropertyDto;

  @JsonCreator
  public ViewProperty(final String singleStringProperty) {
    viewPropertyDto = new StringViewPropertyDto(singleStringProperty);
  }

  @JsonCreator
  private ViewProperty(
      @JsonProperty("name") final String name, @JsonProperty("type") final VariableType type) {
    viewPropertyDto = new VariableViewPropertyDto(name, type);
  }

  // uppercase is intended here to align it with other static fields
  @SuppressWarnings("java:S100")
  public static ViewProperty VARIABLE(final String variableName, final VariableType variableType) {
    return new ViewProperty(variableName, variableType);
  }

  @Override
  public boolean isCombinable(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ViewProperty)) {
      return false;
    }
    final ViewProperty other = (ViewProperty) o;
    return Combinable.isCombinable(viewPropertyDto, other.viewPropertyDto);
  }

  @JsonValue
  public TypedViewPropertyDto getViewPropertyDto() {
    return viewPropertyDto;
  }

  @JsonIgnore
  public <T extends TypedViewPropertyDto> Optional<T> getViewPropertyDtoIfOfType(
      final Class<T> clazz) {
    return Optional.of(viewPropertyDto).filter(clazz::isInstance).map(clazz::cast);
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ViewProperty;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $viewPropertyDto = getViewPropertyDto();
    result = result * PRIME + ($viewPropertyDto == null ? 43 : $viewPropertyDto.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof ViewProperty)) {
      return false;
    }
    final ViewProperty other = (ViewProperty) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$viewPropertyDto = getViewPropertyDto();
    final Object other$viewPropertyDto = other.getViewPropertyDto();
    if (this$viewPropertyDto == null
        ? other$viewPropertyDto != null
        : !this$viewPropertyDto.equals(other$viewPropertyDto)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return viewPropertyDto.toString();
  }
}
