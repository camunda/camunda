/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.result.duration;

import com.google.common.base.MoreObjects;
import lombok.Getter;
import lombok.Setter;
import net.minidev.json.annotate.JsonIgnore;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;

import java.util.Objects;

@Getter
@Setter
public class AggregationResultDto {

  private Long min;
  private Long max;
  private Long avg;
  private Long median;

  public AggregationResultDto() {
  }

  public AggregationResultDto(Long min, Long max, Long avg, Long median) {
    this.min = min;
    this.max = max;
    this.avg = avg;
    this.median = median;
  }

  @JsonIgnore
  public Long getResultForGivenAggregationType(AggregationType aggregationType) {
    switch (aggregationType) {
      case MIN:
        return getMin();
      case MAX:
        return getMax();
      case AVERAGE:
        return getAvg();
      case MEDIAN:
        return getMedian();
      default:
        throw new OptimizeRuntimeException(String.format("Unknown aggregation type [%s]", aggregationType));
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof AggregationResultDto)) {
      return false;
    }
    AggregationResultDto that = (AggregationResultDto) o;
    return Objects.equals(min, that.min) &&
      Objects.equals(max, that.max) &&
      Objects.equals(avg, that.avg) &&
      Objects.equals(median, that.median);
  }

  @Override
  public int hashCode() {
    return Objects.hash(min, max, avg, median);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
      .add("min", min)
      .add("max", max)
      .add("avg", avg)
      .add("median", median)
      .toString();
  }
}
