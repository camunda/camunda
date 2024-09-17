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
import lombok.Data;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = EvaluationDateFilterDto.class, name = "evaluationDateTime"),
  @JsonSubTypes.Type(value = InputVariableFilterDto.class, name = "inputVariable"),
  @JsonSubTypes.Type(value = OutputVariableFilterDto.class, name = "outputVariable"),
})
@Data
public abstract class DecisionFilterDto<DATA extends FilterDataDto> {

  protected DATA data;

  @NotEmpty protected List<String> appliedTo = List.of(ReportConstants.APPLIED_TO_ALL_DEFINITIONS);

  protected DecisionFilterDto(final DATA data) {
    this.data = data;
  }

  public DecisionFilterDto(DATA data, @NotEmpty List<String> appliedTo) {
    this.data = data;
    this.appliedTo = appliedTo;
  }

  public DecisionFilterDto() {}

  @Override
  public String toString() {
    return "DecisionFilter=" + getClass().getSimpleName();
  }
}
