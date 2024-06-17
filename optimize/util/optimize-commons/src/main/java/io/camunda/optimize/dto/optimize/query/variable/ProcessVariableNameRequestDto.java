/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.variable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

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
