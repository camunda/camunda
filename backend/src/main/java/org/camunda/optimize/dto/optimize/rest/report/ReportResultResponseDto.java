/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.rest.report;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.result.ResultType;
import org.camunda.optimize.dto.optimize.rest.pagination.PaginationDto;
import org.camunda.optimize.dto.optimize.rest.report.measure.MeasureResponseDto;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportResultResponseDto<T> {
  private long instanceCount;
  private long instanceCountWithoutFilters;
  private List<MeasureResponseDto<T>> measures = new ArrayList<>();
  private PaginationDto pagination;

  public void addMeasure(MeasureResponseDto<T> measure) {
    this.measures.add(measure);
  }

  @JsonIgnore
  public T getFirstMeasureData() {
    return getMeasures().get(0).getData();
  }

  @JsonIgnore
  public T getData() {
    return getFirstMeasureData();
  }

  // here for API compatibility as the frontend currently makes use of this property
  public ResultType getType() {
    return getMeasures().stream().findFirst().map(MeasureResponseDto::getType).orElse(null);
  }

}
