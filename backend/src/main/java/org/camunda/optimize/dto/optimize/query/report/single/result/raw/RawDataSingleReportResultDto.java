package org.camunda.optimize.dto.optimize.query.report.single.result.raw;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.camunda.optimize.dto.optimize.query.report.single.result.SingleReportResultDto;

import java.util.List;

import static org.camunda.optimize.service.es.report.command.util.ReportConstants.SINGLE_REPORT_TYPE;


@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "reportType")
@JsonSubTypes({
    @JsonSubTypes.Type(value = RawDataSingleReportResultDto.class, name = SINGLE_REPORT_TYPE),
})
public class RawDataSingleReportResultDto extends SingleReportResultDto {

  protected List<RawDataProcessInstanceDto> result;

  public List<RawDataProcessInstanceDto> getResult() {
    return result;
  }

  public void setResult(List<RawDataProcessInstanceDto> result) {
    this.result = result;
  }

}
