package org.camunda.optimize.dto.optimize.query.report.single.result;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.camunda.optimize.dto.optimize.query.report.ReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.raw.RawDataSingleReportResultDto;

import static org.camunda.optimize.service.es.report.command.util.ReportConstants.MAP_RESULT_TYPE;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.NUMBER_RESULT_TYPE;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.RAW_RESULT_TYPE;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "resultType")
@JsonSubTypes({
  @JsonSubTypes.Type(value = MapSingleReportResultDto.class, name = MAP_RESULT_TYPE),
  @JsonSubTypes.Type(value = NumberSingleReportResultDto.class, name = NUMBER_RESULT_TYPE),
  @JsonSubTypes.Type(value = RawDataSingleReportResultDto.class, name = RAW_RESULT_TYPE),
})
public abstract class SingleReportResultDto extends SingleReportDefinitionDto implements ReportResultDto {

  protected long processInstanceCount;

  public long getProcessInstanceCount() {
    return processInstanceCount;
  }

  public void setProcessInstanceCount(long processInstanceCount) {
    this.processInstanceCount = processInstanceCount;
  }
}
