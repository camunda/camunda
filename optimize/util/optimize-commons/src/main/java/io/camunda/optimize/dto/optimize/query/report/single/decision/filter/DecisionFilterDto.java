/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.decision.filter;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.optimize.dto.optimize.ReportConstants;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterDataDto;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = EvaluationDateFilterDto.class, name = "evaluationDateTime"),
  @JsonSubTypes.Type(value = InputVariableFilterDto.class, name = "inputVariable"),
  @JsonSubTypes.Type(value = OutputVariableFilterDto.class, name = "outputVariable"),
})
public abstract class DecisionFilterDto<DATA extends FilterDataDto> {

  protected DATA data;

  @NotEmpty protected List<String> appliedTo = List.of(ReportConstants.APPLIED_TO_ALL_DEFINITIONS);

  protected DecisionFilterDto(final DATA data) {
    this.data = data;
  }

  public DecisionFilterDto(final DATA data, @NotEmpty final List<String> appliedTo) {
    this.data = data;
    this.appliedTo = appliedTo;
  }

  public DecisionFilterDto() {}

  public DATA getData() {
    return data;
  }

  public void setData(final DATA data) {
    this.data = data;
  }

  public @NotEmpty List<String> getAppliedTo() {
    return appliedTo;
  }

  public void setAppliedTo(@NotEmpty final List<String> appliedTo) {
    this.appliedTo = appliedTo;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof DecisionFilterDto;
  }

  @Override
  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public String toString() {
    return "DecisionFilter=" + getClass().getSimpleName();
  }
}
