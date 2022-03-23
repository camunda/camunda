/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.distributed;

import org.camunda.optimize.dto.optimize.query.report.single.configuration.DistributedByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.value.VariableDistributedByValueDto;

import java.util.Optional;

public class VariableDistributedByDto extends ProcessReportDistributedByDto<VariableDistributedByValueDto> {

  public VariableDistributedByDto() {
    this.type = DistributedByType.VARIABLE;
  }

  @Override
  public String toString() {
    return super.toString() +
      Optional.ofNullable(this.getValue())
        .map(valueDto -> "_" + this.getValue().getName() + "_" + getValue().getType())
        .orElse("");
  }
}
