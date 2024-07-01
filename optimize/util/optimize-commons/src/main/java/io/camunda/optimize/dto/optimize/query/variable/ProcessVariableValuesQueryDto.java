/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.variable;

import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.SingleReportDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProcessVariableValuesQueryDto {

  private List<ProcessVariableSourceDto> processVariableSources;
  private String name;
  private VariableType type;
  private String valueFilter;
  private Integer resultOffset;
  private Integer numResults;

  public static ProcessVariableValuesQueryDto fromProcessVariableValueRequestDto(
      final ProcessVariableValueRequestDto requestDto) {
    return ProcessVariableValuesQueryDto.builder()
        .name(requestDto.getName())
        .type(requestDto.getType())
        .resultOffset(requestDto.getResultOffset())
        .valueFilter(requestDto.getValueFilter())
        .numResults(requestDto.getNumResults())
        .processVariableSources(
            Collections.singletonList(
                ProcessVariableSourceDto.builder()
                    .processInstanceId(requestDto.getProcessInstanceId())
                    .processDefinitionKey(requestDto.getProcessDefinitionKey())
                    .processDefinitionVersions(requestDto.getProcessDefinitionVersions())
                    .tenantIds(requestDto.getTenantIds())
                    .build()))
        .build();
  }

  public static ProcessVariableValuesQueryDto fromProcessVariableReportValuesRequest(
      final ProcessVariableReportValuesRequestDto requestDto,
      final List<ReportDefinitionDto> reports) {
    final List<ProcessVariableSourceDto> reportSources =
        reports.stream()
            .filter(SingleReportDefinitionDto.class::isInstance)
            .map(report -> (SingleReportDefinitionDto<?>) report)
            .map(SingleReportDefinitionDto::getData)
            .map(SingleReportDataDto::getDefinitions)
            .flatMap(Collection::stream)
            .map(
                definitionDto ->
                    ProcessVariableSourceDto.builder()
                        .processDefinitionKey(definitionDto.getKey())
                        .processDefinitionVersions(definitionDto.getVersions())
                        .tenantIds(definitionDto.getTenantIds())
                        .build())
            .collect(Collectors.toList());
    return ProcessVariableValuesQueryDto.builder()
        .name(requestDto.getName())
        .type(requestDto.getType())
        .resultOffset(requestDto.getResultOffset())
        .valueFilter(requestDto.getValueFilter())
        .numResults(requestDto.getNumResults())
        .processVariableSources(reportSources)
        .build();
  }
}
