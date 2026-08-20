/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report;

import io.camunda.optimize.dto.optimize.ReportType;
import io.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;

public abstract class SingleReportDefinitionDto<RD extends SingleReportDataDto>
    extends ReportDefinitionDto<RD> {

  protected SingleReportDefinitionDto(
      final RD data, final Boolean combined, final ReportType reportType) {
    super(data, combined, reportType);
  }

  protected SingleReportDefinitionDto(final SingleReportDefinitionDtoBuilder<RD, ?, ?> b) {
    super(b);
  }

  public abstract static class SingleReportDefinitionDtoBuilder<
          RD extends SingleReportDataDto,
          C extends SingleReportDefinitionDto<RD>,
          B extends SingleReportDefinitionDtoBuilder<RD, C, B>>
      extends ReportDefinitionDtoBuilder<RD, C, B> {

    @Override
    protected abstract B self();

    @Override
    public abstract C build();

    @Override
    public String toString() {
      return "SingleReportDefinitionDto.SingleReportDefinitionDtoBuilder(super="
          + super.toString()
          + ")";
    }
  }
}
