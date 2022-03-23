/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;

import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
@JsonSubTypes({
  @JsonSubTypes.Type(value = CombinedReportDataDto.class),
  @JsonSubTypes.Type(value = SingleReportDataDto.class),
})
public interface ReportDataDto {
  String createCommandKey();
  List<String> createCommandKeys();
}
