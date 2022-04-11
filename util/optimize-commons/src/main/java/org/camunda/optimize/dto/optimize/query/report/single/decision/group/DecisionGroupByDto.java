/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.decision.group;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;
import org.camunda.optimize.dto.optimize.query.report.Combinable;
import org.camunda.optimize.dto.optimize.query.report.single.decision.group.value.DecisionGroupByValueDto;

import java.util.Objects;

import static org.camunda.optimize.dto.optimize.ReportConstants.GROUP_BY_EVALUATION_DATE_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.GROUP_BY_INPUT_VARIABLE_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.GROUP_BY_MATCHED_RULE_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.GROUP_BY_NONE_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.GROUP_BY_OUTPUT_VARIABLE_TYPE;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = DecisionGroupByNoneDto.class, name = GROUP_BY_NONE_TYPE),
  @JsonSubTypes.Type(value = DecisionGroupByEvaluationDateTimeDto.class, name = GROUP_BY_EVALUATION_DATE_TYPE),
  @JsonSubTypes.Type(value = DecisionGroupByInputVariableDto.class, name = GROUP_BY_INPUT_VARIABLE_TYPE),
  @JsonSubTypes.Type(value = DecisionGroupByOutputVariableDto.class, name = GROUP_BY_OUTPUT_VARIABLE_TYPE),
  @JsonSubTypes.Type(value = DecisionGroupByMatchedRuleDto.class, name = GROUP_BY_MATCHED_RULE_TYPE)
}
)
@Data
public abstract class DecisionGroupByDto<VALUE extends DecisionGroupByValueDto> implements Combinable {

  @JsonProperty
  protected DecisionGroupByType type;
  protected VALUE value;

  @Override
  public String toString() {
    return type.getId();
  }

  @Override
  public boolean isCombinable(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof DecisionGroupByDto)) {
      return false;
    }
    DecisionGroupByDto<?> that = (DecisionGroupByDto<?>) o;
    return Objects.equals(type, that.type) && Combinable.isCombinable(value, that.value);
  }

  @JsonIgnore
  public String createCommandKey() {
    return type.getId();
  }


}
