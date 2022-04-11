/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.decision.view;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
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
    return getProperties().stream().findFirst().map(ViewProperty::toString).orElse(null);
  }

  @JsonSetter
  public void setProperties(final List<ViewProperty> properties) {
    this.properties = new ArrayList<>(properties);
  }

  public void setProperties(final ViewProperty... properties) {
    this.properties = Arrays.asList(properties);
  }

}
