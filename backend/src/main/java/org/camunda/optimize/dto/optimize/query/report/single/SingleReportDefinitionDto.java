package org.camunda.optimize.dto.optimize.query.report.single;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeId;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportType;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;

import static org.camunda.optimize.dto.optimize.ReportConstants.PROCESS_REPORT_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.DECISION_REPORT_TYPE;

public class SingleReportDefinitionDto<T extends SingleReportDataDto> extends ReportDefinitionDto<T> {

  public static final String REPORT_DEFINITION_FIELD_REPORT_TYPE = "reportType";

  public SingleReportDefinitionDto(T data) {
    this();
    setData(data);
  }

  public SingleReportDefinitionDto() {
    this.combined = false;
  }

  @JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
    property = REPORT_DEFINITION_FIELD_REPORT_TYPE
  )
  @JsonSubTypes({
    @JsonSubTypes.Type(value = ProcessReportDataDto.class, name = PROCESS_REPORT_TYPE),
    @JsonSubTypes.Type(value = DecisionReportDataDto.class, name = DECISION_REPORT_TYPE)
  })
  @Override
  public T getData() {
    return super.getData();
  }

  @JsonTypeId
  @Override
  public ReportType getReportType() {
    return super.getReportType();
  }
}
