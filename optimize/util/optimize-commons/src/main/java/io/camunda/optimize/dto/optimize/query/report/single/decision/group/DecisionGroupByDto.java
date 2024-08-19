/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.decision.group;

import static io.camunda.optimize.dto.optimize.ReportConstants.GROUP_BY_EVALUATION_DATE_TYPE;
import static io.camunda.optimize.dto.optimize.ReportConstants.GROUP_BY_INPUT_VARIABLE_TYPE;
import static io.camunda.optimize.dto.optimize.ReportConstants.GROUP_BY_MATCHED_RULE_TYPE;
import static io.camunda.optimize.dto.optimize.ReportConstants.GROUP_BY_NONE_TYPE;
import static io.camunda.optimize.dto.optimize.ReportConstants.GROUP_BY_OUTPUT_VARIABLE_TYPE;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.optimize.dto.optimize.query.report.Combinable;
import io.camunda.optimize.dto.optimize.query.report.single.decision.group.value.DecisionGroupByValueDto;
import java.util.Objects;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = DecisionGroupByNoneDto.class, name = GROUP_BY_NONE_TYPE),
  @JsonSubTypes.Type(
      value = DecisionGroupByEvaluationDateTimeDto.class,
      name = GROUP_BY_EVALUATION_DATE_TYPE),
  @JsonSubTypes.Type(
      value = DecisionGroupByInputVariableDto.class,
      name = GROUP_BY_INPUT_VARIABLE_TYPE),
  @JsonSubTypes.Type(
      value = DecisionGroupByOutputVariableDto.class,
      name = GROUP_BY_OUTPUT_VARIABLE_TYPE),
  @JsonSubTypes.Type(value = DecisionGroupByMatchedRuleDto.class, name = GROUP_BY_MATCHED_RULE_TYPE)
})
public abstract class DecisionGroupByDto<VALUE extends DecisionGroupByValueDto>
    implements Combinable {

  @JsonProperty protected DecisionGroupByType type;
  protected VALUE value;

  public DecisionGroupByDto() {}

  @Override
  public boolean isCombinable(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof DecisionGroupByDto)) {
      return false;
    }
    final DecisionGroupByDto<?> that = (DecisionGroupByDto<?>) o;
    return Objects.equals(type, that.type) && Combinable.isCombinable(value, that.value);
  }

  @JsonIgnore
  public String createCommandKey() {
    return type.getId();
  }

  public DecisionGroupByType getType() {
    return type;
  }

  @JsonProperty
  public void setType(final DecisionGroupByType type) {
    this.type = type;
  }

  public VALUE getValue() {
    return value;
  }

  public void setValue(final VALUE value) {
    this.value = value;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof DecisionGroupByDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $type = getType();
    result = result * PRIME + ($type == null ? 43 : $type.hashCode());
    final Object $value = getValue();
    result = result * PRIME + ($value == null ? 43 : $value.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof DecisionGroupByDto)) {
      return false;
    }
    final DecisionGroupByDto<?> other = (DecisionGroupByDto<?>) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$type = getType();
    final Object other$type = other.getType();
    if (this$type == null ? other$type != null : !this$type.equals(other$type)) {
      return false;
    }
    final Object this$value = getValue();
    final Object other$value = other.getValue();
    if (this$value == null ? other$value != null : !this$value.equals(other$value)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return type.getId();
  }
}
