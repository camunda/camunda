/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.process.filter;

import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import java.util.Collections;
import java.util.List;

public class InstanceEndDateFilterDto extends ProcessFilterDto<DateFilterDataDto<?>> {

  public InstanceEndDateFilterDto() {}

  @Override
  public List<FilterApplicationLevel> validApplicationLevels() {
    return Collections.singletonList(FilterApplicationLevel.INSTANCE);
  }
}
