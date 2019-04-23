/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.decision.view;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import org.camunda.optimize.dto.optimize.query.report.Combinable;

import java.util.Objects;

public class DecisionViewDto implements Combinable {

  @Getter @Setter protected DecisionViewProperty property;

  public DecisionViewDto() {
    super();
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
    return property.toString();
  }

  @Override
  public String toString() {
    return "DecisionViewDto{" +
      "property='" + property + '\'' +
      '}';
  }
}
