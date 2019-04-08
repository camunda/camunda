/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.decision.view;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.camunda.optimize.dto.optimize.query.report.Combinable;

import java.util.Objects;

public class DecisionViewDto implements Combinable {

  protected DecisionViewOperation operation;
  protected DecisionViewProperty property;

  public DecisionViewDto() {
    super();
  }

  public DecisionViewDto(DecisionViewOperation operation) {
    this.operation = operation;
  }

  public DecisionViewOperation getOperation() {
    return operation;
  }

  public void setOperation(DecisionViewOperation operation) {
    this.operation = operation;
  }

  public DecisionViewProperty getProperty() {
    return property;
  }

  public void setProperty(DecisionViewProperty property) {
    this.property = property;
  }

  @Override
  public boolean isCombinable(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof DecisionViewDto)) {
      return false;
    }
    DecisionViewDto viewDto = (DecisionViewDto) o;
    // note: different view operations are okay, since users might want to
    // compare the results of those in a combined report.
    return Objects.equals(property, viewDto.property);
  }

  @JsonIgnore
  public String createCommandKey() {
    String separator = "-";
    return operation + separator +  property;
  }

  @Override
  public String toString() {
    return "DecisionViewDto{" +
      "operation='" + operation + '\'' +
      ", property='" + property + '\'' +
      '}';
  }
}
