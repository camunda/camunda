package org.camunda.optimize.dto.optimize.query.report.single.decision.result;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.camunda.optimize.dto.optimize.query.report.ReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ResultType;

import static org.camunda.optimize.dto.optimize.ReportConstants.RAW_RESULT_TYPE;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "resultType")
@JsonSubTypes({
  @JsonSubTypes.Type(value = RawDataDecisionReportResultDto.class, name = RAW_RESULT_TYPE),
})
public abstract class DecisionReportResultDto extends SingleReportDefinitionDto<DecisionReportDataDto> implements ReportResultDto {

  protected long decisionInstanceCount;

  public long getDecisionInstanceCount() {
    return decisionInstanceCount;
  }

  public void setDecisionInstanceCount(final long decisionInstanceCount) {
    this.decisionInstanceCount = decisionInstanceCount;
  }

  public abstract ResultType getResultType();

}
