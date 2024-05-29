/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.dto.optimize.query.report;

import io.camunda.optimize.dto.optimize.ReportType;
import io.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import lombok.experimental.SuperBuilder;

@SuperBuilder
public abstract class SingleReportDefinitionDto<RD extends SingleReportDataDto>
    extends ReportDefinitionDto<RD> {

  protected SingleReportDefinitionDto(
      final RD data, final Boolean combined, final ReportType reportType) {
    super(data, combined, reportType);
  }
}
