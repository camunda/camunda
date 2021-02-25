/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.decision.view;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Data
@FieldNameConstants
public class DecisionViewDto  {

  protected List<ViewProperty> properties = new ArrayList<>();

  public DecisionViewDto(final ViewProperty property) {
    this.getProperties().add(property);
  }

  @JsonIgnore
  public String createCommandKey() {
    return getProperty().toString();
  }

  // to be removed with OPT-4872, just here for jackson and API backwards compatibility thus protected
  @Deprecated
  public ViewProperty getProperty() {
    return this.properties != null && !this.properties.isEmpty() ? properties.get(0) : null;
  }

  // to be removed with OPT-4872, just here for jackson and API backwards compatibility thus protected
  @Deprecated
  public void setProperty(final ViewProperty property) {
    if (this.properties == null || this.properties.isEmpty()) {
      this.properties = Arrays.asList(property);
    } else {
      this.properties.set(0, property);
    }
  }

  public void setProperties(final ViewProperty... properties) {
    this.properties = Arrays.asList(properties);
  }

}
