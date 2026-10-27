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

public class CombinedReportDefinitionRequestDto extends ReportDefinitionDto<CombinedReportDataDto> {

  public CombinedReportDefinitionRequestDto() {
    this(new CombinedReportDataDto());
  }

  public CombinedReportDefinitionRequestDto(final CombinedReportDataDto data) {
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

  public static CombinedReportDefinitionRequestDtoBuilder<?, ?> builder() {
    return new CombinedReportDefinitionRequestDtoBuilderImpl();
  }

  public abstract static class CombinedReportDefinitionRequestDtoBuilder<
          C extends CombinedReportDefinitionRequestDto,
          B extends CombinedReportDefinitionRequestDtoBuilder<C, B>>
      extends ReportDefinitionDtoBuilder<CombinedReportDataDto, C, B> {

    @Override
    protected abstract B self();

    @Override
    public abstract C build();

    @Override
    public String toString() {
      return "CombinedReportDefinitionRequestDto.CombinedReportDefinitionRequestDtoBuilder(super="
          + super.toString()
          + ")";
    }
  }

  private static final class CombinedReportDefinitionRequestDtoBuilderImpl
      extends CombinedReportDefinitionRequestDtoBuilder<
          CombinedReportDefinitionRequestDto, CombinedReportDefinitionRequestDtoBuilderImpl> {

    private CombinedReportDefinitionRequestDtoBuilderImpl() {}

    @Override
    protected CombinedReportDefinitionRequestDtoBuilderImpl self() {
      return this;
    }

    @Override
    public CombinedReportDefinitionRequestDto build() {
      return new CombinedReportDefinitionRequestDto(this.getData());
    }
  }
}
