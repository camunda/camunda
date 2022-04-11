/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.combined;

import com.google.common.collect.ImmutableMap;
import lombok.experimental.SuperBuilder;
import org.camunda.optimize.dto.optimize.ReportType;
import org.camunda.optimize.dto.optimize.query.entity.EntityResponseDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityType;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;

@SuperBuilder
public class CombinedReportDefinitionRequestDto extends ReportDefinitionDto<CombinedReportDataDto> {

  public CombinedReportDefinitionRequestDto() {
    this(new CombinedReportDataDto());
  }

  public CombinedReportDefinitionRequestDto(CombinedReportDataDto data) {
    super(data, true, ReportType.PROCESS);
  }

  @Override
  public EntityResponseDto toEntityDto() {
    final EntityResponseDto entityDto = super.toEntityDto();
    entityDto.getData().setSubEntityCounts(ImmutableMap.of(EntityType.REPORT, (long) getData().getReports().size()));
    return entityDto;
  }
}
