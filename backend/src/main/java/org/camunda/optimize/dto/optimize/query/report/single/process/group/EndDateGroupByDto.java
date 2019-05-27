/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.group;

import org.camunda.optimize.dto.optimize.query.report.single.process.group.value.DateGroupByValueDto;

public class EndDateGroupByDto extends ProcessGroupByDto<DateGroupByValueDto> {

  public EndDateGroupByDto() {
    this.type = ProcessGroupByType.END_DATE;
  }

  @Override
  public String toString() {
    return super.toString() + "_" + this.getValue().getUnit();
  }


}
