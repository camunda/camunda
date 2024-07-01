/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.combined;

import com.google.common.collect.ImmutableMap;
import io.camunda.optimize.dto.optimize.ReportType;
import io.camunda.optimize.dto.optimize.RoleType;
import io.camunda.optimize.dto.optimize.query.entity.EntityResponseDto;
import io.camunda.optimize.dto.optimize.query.entity.EntityType;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import lombok.experimental.SuperBuilder;

@SuperBuilder
public class CombinedReportDefinitionRequestDto extends ReportDefinitionDto<CombinedReportDataDto> {

  public CombinedReportDefinitionRequestDto() {
    this(new CombinedReportDataDto());
  }

  public CombinedReportDefinitionRequestDto(CombinedReportDataDto data) {
    super(data, true, ReportType.PROCESS);
  }

  @Override
  public EntityResponseDto toEntityDto(final RoleType roleType) {
    final EntityResponseDto entityDto = super.toEntityDto(roleType);
    entityDto
        .getData()
        .setSubEntityCounts(
            ImmutableMap.of(EntityType.REPORT, (long) getData().getReports().size()));
    return entityDto;
  }
}
