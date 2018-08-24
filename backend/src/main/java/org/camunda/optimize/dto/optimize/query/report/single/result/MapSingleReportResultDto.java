package org.camunda.optimize.dto.optimize.query.report.single.result;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.HashMap;
import java.util.Map;

import static org.camunda.optimize.service.es.report.command.util.ReportConstants.SINGLE_REPORT_TYPE;


@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "reportType")
@JsonSubTypes({
    @JsonSubTypes.Type(value = MapSingleReportResultDto.class, name = SINGLE_REPORT_TYPE),
})
public class MapSingleReportResultDto extends SingleReportResultDto {

  private Map<String, Long> result = new HashMap<>();

  public Map<String, Long> getResult() {
    return result;
  }

  public void setResult(Map<String, Long> result) {
    this.result = result;
  }
}
