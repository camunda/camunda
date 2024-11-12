/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable;

import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.data.BooleanVariableFilterSubDataDto;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import java.util.List;

public class BooleanVariableFilterDataDto
    extends VariableFilterDataDto<BooleanVariableFilterSubDataDto> {
  protected BooleanVariableFilterDataDto() {
    this(null, null);
  }

  public BooleanVariableFilterDataDto(final String name, final List<Boolean> values) {
    super(name, VariableType.BOOLEAN, new BooleanVariableFilterSubDataDto(values));
  }
}
