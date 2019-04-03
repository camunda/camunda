package org.camunda.optimize.dto.optimize.query.report.single.process.result.raw;

import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.LimitedResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ResultType;

import java.util.List;

public class RawDataProcessReportResultDto extends ProcessReportResultDto implements LimitedResultDto {

  protected List<RawDataProcessInstanceDto> data;
  private Boolean isComplete = true;

  @Override
  public Boolean getIsComplete() {
    return isComplete;
  }

  public void setComplete(final Boolean complete) {
    isComplete = complete;
  }

  public List<RawDataProcessInstanceDto> getData() {
    return data;
  }

  public void setData(List<RawDataProcessInstanceDto> data) {
    this.data = data;
  }

  @Override
  public ResultType getType() {
    return ResultType.RAW;
  }

}
