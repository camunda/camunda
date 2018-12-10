package org.camunda.optimize.dto.optimize.query.report.single.process.result;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.camunda.optimize.dto.optimize.query.report.ReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ResultType;

import static org.camunda.optimize.dto.optimize.ReportConstants.MAP_RESULT_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.NUMBER_RESULT_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.RAW_RESULT_TYPE;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "resultType")
@JsonSubTypes({
  @JsonSubTypes.Type(value = MapProcessReportResultDto.class, name = MAP_RESULT_TYPE),
  @JsonSubTypes.Type(value = NumberProcessReportResultDto.class, name = NUMBER_RESULT_TYPE),
  @JsonSubTypes.Type(value = RawDataProcessReportResultDto.class, name = RAW_RESULT_TYPE),
})
public abstract class ProcessReportResultDto extends SingleReportDefinitionDto<ProcessReportDataDto> implements ReportResultDto {

  protected long processInstanceCount;

  public long getProcessInstanceCount() {
    return processInstanceCount;
  }

  public void setProcessInstanceCount(long processInstanceCount) {
    this.processInstanceCount = processInstanceCount;
  }

  public abstract ResultType getResultType();

}
