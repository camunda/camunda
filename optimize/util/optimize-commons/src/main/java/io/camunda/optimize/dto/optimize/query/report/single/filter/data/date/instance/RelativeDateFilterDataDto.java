/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.instance;

import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterType;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RelativeDateFilterStartDto;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
public class RelativeDateFilterDataDto extends DateFilterDataDto<RelativeDateFilterStartDto> {
  public RelativeDateFilterDataDto() {
    this(null);
  }

  public RelativeDateFilterDataDto(final RelativeDateFilterStartDto relativeDateFilterStartDto) {
    super(DateFilterType.RELATIVE, relativeDateFilterStartDto, null);
  }
}
