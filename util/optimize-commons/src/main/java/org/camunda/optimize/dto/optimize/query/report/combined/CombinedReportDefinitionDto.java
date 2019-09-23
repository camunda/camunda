/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.combined;

import com.google.common.collect.ImmutableMap;
import org.camunda.optimize.dto.optimize.ReportType;
import org.camunda.optimize.dto.optimize.query.entity.EntityDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityType;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;

public class CombinedReportDefinitionDto extends ReportDefinitionDto<CombinedReportDataDto> {

  public CombinedReportDefinitionDto() {
    this(new CombinedReportDataDto());
  }

  public CombinedReportDefinitionDto(CombinedReportDataDto data) {
    super(data, true, ReportType.PROCESS);
  }

  @Override
  public EntityDto toEntityDto() {
    final EntityDto entityDto = super.toEntityDto();
    entityDto.getData().setSubEntityCounts(ImmutableMap.of(EntityType.REPORT, (long) getData().getReports().size()));
    return entityDto;
  }
}
