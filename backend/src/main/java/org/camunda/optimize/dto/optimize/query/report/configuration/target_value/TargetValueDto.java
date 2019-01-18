package org.camunda.optimize.dto.optimize.query.report.configuration.target_value;

import java.util.Objects;

public class TargetValueDto {

  private CountChartDto countChart = new CountChartDto();
  private DurationProgressDto durationProgress = new DurationProgressDto();
  private Boolean active = false;
  private CountProgressDto countProgress = new CountProgressDto();
  private DurationChartDto durationChart = new DurationChartDto();

  public CountChartDto getCountChart() {
    return countChart;
  }

  public void setCountChart(CountChartDto countChart) {
    this.countChart = countChart;
  }

  public DurationProgressDto getDurationProgress() {
    return durationProgress;
  }

  public void setDurationProgress(DurationProgressDto durationProgress) {
    this.durationProgress = durationProgress;
  }

  public Boolean getActive() {
    return active;
  }

  public void setActive(Boolean active) {
    this.active = active;
  }

  public CountProgressDto getCountProgress() {
    return countProgress;
  }

  public void setCountProgress(CountProgressDto countProgress) {
    this.countProgress = countProgress;
  }

  public DurationChartDto getDurationChart() {
    return durationChart;
  }

  public void setDurationChart(DurationChartDto durationChart) {
    this.durationChart = durationChart;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof TargetValueDto)) {
      return false;
    }
    TargetValueDto that = (TargetValueDto) o;
    return Objects.equals(countChart, that.countChart) &&
      Objects.equals(durationProgress, that.durationProgress) &&
      Objects.equals(active, that.active) &&
      Objects.equals(countProgress, that.countProgress) &&
      Objects.equals(durationChart, that.durationChart);
  }

  @Override
  public int hashCode() {
    return Objects.hash(countChart, durationProgress, active, countProgress, durationChart);
  }
}
