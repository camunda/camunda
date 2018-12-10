package org.camunda.optimize.dto.optimize.query.report;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;

@JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
@JsonSubTypes({
  @JsonSubTypes.Type(value = CombinedReportDataDto.class),
  @JsonSubTypes.Type(value = SingleReportDataDto.class),
})
public interface ReportDataDto {
  String createCommandKey();
}
