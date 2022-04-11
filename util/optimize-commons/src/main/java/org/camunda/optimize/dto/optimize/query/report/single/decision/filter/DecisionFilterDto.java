/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.decision.filter;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterDataDto;

import javax.validation.constraints.NotEmpty;
import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = EvaluationDateFilterDto.class, name = "evaluationDateTime"),
  @JsonSubTypes.Type(value = InputVariableFilterDto.class, name = "inputVariable"),
  @JsonSubTypes.Type(value = OutputVariableFilterDto.class, name = "outputVariable"),
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class DecisionFilterDto<DATA extends FilterDataDto> {
  protected DATA data;

  @NotEmpty
  protected List<String> appliedTo = List.of(ReportConstants.APPLIED_TO_ALL_DEFINITIONS);

  protected DecisionFilterDto(final DATA data) {
    this.data = data;
  }

  @Override
  public String toString() {
    return "DecisionFilter=" + getClass().getSimpleName();
  }
}
