/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.dashboard.filter;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterDataDto;

@Data
@NoArgsConstructor
@FieldNameConstants
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = DashboardInstanceStartDateFilterDto.class, name = "instanceStartDate"),
  @JsonSubTypes.Type(value = DashboardInstanceEndDateFilterDto.class, name = "instanceEndDate"),
  @JsonSubTypes.Type(value = DashboardStateFilterDto.class, name = "state"),
  @JsonSubTypes.Type(value = DashboardVariableFilterDto.class, name = "variable"),
  @JsonSubTypes.Type(value = DashboardAssigneeFilterDto.class, name = "assignee"),
  @JsonSubTypes.Type(value = DashboardCandidateGroupFilterDto.class, name = "candidateGroup")
})
public abstract class DashboardFilterDto<DATA extends FilterDataDto> {
  protected DATA data;

  protected DashboardFilterDto(final DATA data) {
    this.data = data;
  }
}
