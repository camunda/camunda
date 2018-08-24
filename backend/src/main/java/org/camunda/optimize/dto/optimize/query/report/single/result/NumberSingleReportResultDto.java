package org.camunda.optimize.dto.optimize.query.report.single.result;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import static org.camunda.optimize.service.es.report.command.util.ReportConstants.SINGLE_REPORT_TYPE;


@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "reportType")
@JsonSubTypes({
    @JsonSubTypes.Type(value = NumberSingleReportResultDto.class, name = SINGLE_REPORT_TYPE),
})
public class NumberSingleReportResultDto extends SingleReportResultDto {

  private long result;

  public long getResult() {
    return result;
  }

  public void setResult(long result) {
    this.result = result;
  }
}
