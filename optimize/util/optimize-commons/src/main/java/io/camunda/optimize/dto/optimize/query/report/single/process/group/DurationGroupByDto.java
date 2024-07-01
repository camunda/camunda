/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.process.group;

import io.camunda.optimize.dto.optimize.query.report.single.process.group.value.DateGroupByValueDto;
import java.util.Optional;

public class DurationGroupByDto extends ProcessGroupByDto<DateGroupByValueDto> {
  public DurationGroupByDto() {
    this.type = ProcessGroupByType.DURATION;
  }

  @Override
  public String toString() {
    return super.toString()
        + Optional.ofNullable(this.getValue()).map(valueDto -> "_" + valueDto.getUnit()).orElse("");
  }
}
