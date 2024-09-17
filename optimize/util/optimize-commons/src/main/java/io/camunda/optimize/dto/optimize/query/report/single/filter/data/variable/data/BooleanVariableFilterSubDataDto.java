/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.data;

import java.util.List;
import lombok.Data;

@Data
public class BooleanVariableFilterSubDataDto {

  protected List<Boolean> values;

  public BooleanVariableFilterSubDataDto(final List<Boolean> values) {
    this.values = values;
  }

  protected BooleanVariableFilterSubDataDto() {}
}
