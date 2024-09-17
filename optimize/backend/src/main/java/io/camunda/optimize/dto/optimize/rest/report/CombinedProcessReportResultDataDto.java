/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest.report;

import java.util.Map;
import lombok.Data;

@Data
public class CombinedProcessReportResultDataDto<T> {

  protected Map<String, AuthorizedProcessReportEvaluationResponseDto<T>> data;
  private long instanceCount;

  public CombinedProcessReportResultDataDto(
      Map<String, AuthorizedProcessReportEvaluationResponseDto<T>> data, long instanceCount) {
    this.data = data;
    this.instanceCount = instanceCount;
  }

  protected CombinedProcessReportResultDataDto() {}
}
