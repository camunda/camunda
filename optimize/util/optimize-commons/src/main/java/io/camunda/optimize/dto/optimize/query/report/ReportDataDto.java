/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
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
