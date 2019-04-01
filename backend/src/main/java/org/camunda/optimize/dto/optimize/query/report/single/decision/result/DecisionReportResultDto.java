package org.camunda.optimize.dto.optimize.query.report.single.decision.result;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.camunda.optimize.dto.optimize.query.report.ReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionReportResultDto;

import static org.camunda.optimize.dto.optimize.ReportConstants.FREQUENCY_MAP_RESULT_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.FREQUENCY_NUMBER_RESULT_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.RAW_RESULT_TYPE;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = DecisionReportMapResultDto.class, name = FREQUENCY_MAP_RESULT_TYPE),
  @JsonSubTypes.Type(value = DecisionReportNumberResultDto.class, name = FREQUENCY_NUMBER_RESULT_TYPE),
  @JsonSubTypes.Type(value = RawDataDecisionReportResultDto.class, name = RAW_RESULT_TYPE),
})
public abstract class DecisionReportResultDto implements ReportResultDto {

  protected long decisionInstanceCount;

  public long getDecisionInstanceCount() {
    return decisionInstanceCount;
  }

  public void setDecisionInstanceCount(final long decisionInstanceCount) {
    this.decisionInstanceCount = decisionInstanceCount;
  }

}
