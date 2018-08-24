package org.camunda.optimize.dto.optimize.query.report.combined;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.Map;

import static org.camunda.optimize.service.es.report.command.util.ReportConstants.COMBINED_REPORT_TYPE;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "reportType")
@JsonSubTypes({
    @JsonSubTypes.Type(value = CombinedMapReportResultDto.class, name = COMBINED_REPORT_TYPE),
})
public class CombinedMapReportResultDto extends CombinedReportResultDto<Map<String, Long>> {

}
