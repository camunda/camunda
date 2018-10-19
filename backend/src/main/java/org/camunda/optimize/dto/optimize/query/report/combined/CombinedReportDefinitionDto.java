package org.camunda.optimize.dto.optimize.query.report.combined;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;

import static org.camunda.optimize.service.es.report.command.util.ReportConstants.COMBINED_REPORT_TYPE;

@JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
@JsonSubTypes({
  @JsonSubTypes.Type(value = CombinedReportResultDto.class),
})
public class CombinedReportDefinitionDto extends ReportDefinitionDto<CombinedReportDataDto> {

  public CombinedReportDefinitionDto() {
    this.reportType = COMBINED_REPORT_TYPE;
  }
}
