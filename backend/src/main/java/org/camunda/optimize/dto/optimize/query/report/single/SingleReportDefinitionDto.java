package org.camunda.optimize.dto.optimize.query.report.single;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.SingleReportResultDto;

import static org.camunda.optimize.service.es.report.command.util.ReportConstants.SINGLE_REPORT_TYPE;

@JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
@JsonSubTypes({
  @JsonSubTypes.Type(value = SingleReportResultDto.class),
})
public class SingleReportDefinitionDto extends ReportDefinitionDto<SingleReportDataDto> {

  public SingleReportDefinitionDto() {
    this.reportType = SINGLE_REPORT_TYPE;
  }
}
