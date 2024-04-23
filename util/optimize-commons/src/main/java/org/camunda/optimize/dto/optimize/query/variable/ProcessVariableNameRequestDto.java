/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.variable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;

@NoArgsConstructor
@Data
public class ProcessVariableNameRequestDto {
  private List<ProcessToQueryDto> processesToQuery = new ArrayList<>();
  private List<ProcessFilterDto<?>> filter = new ArrayList<>();

  @JsonIgnore private ZoneId timezone = ZoneId.systemDefault();

  public ProcessVariableNameRequestDto(
      List<ProcessToQueryDto> processesToQuery, List<ProcessFilterDto<?>> filter) {
    this.processesToQuery = processesToQuery;
    this.filter = filter;
    timezone = ZoneId.systemDefault();
  }

  public ProcessVariableNameRequestDto(List<ProcessToQueryDto> processesToQuery) {
    this(processesToQuery, Collections.emptyList());
  }
}
