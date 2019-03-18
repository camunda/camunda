package org.camunda.optimize.dto.optimize.query.report.single.process.result.duration;

import java.util.Objects;

public class OperationResultDto {

  private Long min;
  private Long max;
  private Long avg;
  private Long median;

  public OperationResultDto() {
  }

  public OperationResultDto(Long min, Long max, Long avg, Long median) {
    this.min = min;
    this.max = max;
    this.avg = avg;
    this.median = median;
  }

  public Long getMin() {
    return min;
  }

  public void setMin(Long min) {
    this.min = min;
  }

  public Long getMax() {
    return max;
  }

  public void setMax(Long max) {
    this.max = max;
  }

  public Long getAvg() {
    return avg;
  }

  public void setAvg(Long avg) {
    this.avg = avg;
  }

  public Long getMedian() {
    return median;
  }

  public void setMedian(Long median) {
    this.median = median;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof OperationResultDto)) {
      return false;
    }
    OperationResultDto that = (OperationResultDto) o;
    return Objects.equals(min, that.min) &&
      Objects.equals(max, that.max) &&
      Objects.equals(avg, that.avg) &&
      Objects.equals(median, that.median);
  }

  @Override
  public int hashCode() {
    return Objects.hash(min, max, avg, median);
  }
}
