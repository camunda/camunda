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

public class ProcessVariableValuesQueryDto {

  private List<ProcessVariableSourceDto> processVariableSources;
  private String name;
  private VariableType type;
  private String valueFilter;
  private Integer resultOffset;
  private Integer numResults;

  ProcessVariableValuesQueryDto(
      final List<ProcessVariableSourceDto> processVariableSources,
      final String name,
      final VariableType type,
      final String valueFilter,
      final Integer resultOffset,
      final Integer numResults) {
    this.processVariableSources = processVariableSources;
    this.name = name;
    this.type = type;
    this.valueFilter = valueFilter;
    this.resultOffset = resultOffset;
    this.numResults = numResults;
  }

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

  public List<ProcessVariableSourceDto> getProcessVariableSources() {
    return processVariableSources;
  }

  public void setProcessVariableSources(
      final List<ProcessVariableSourceDto> processVariableSources) {
    this.processVariableSources = processVariableSources;
  }

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public VariableType getType() {
    return type;
  }

  public void setType(final VariableType type) {
    this.type = type;
  }

  public String getValueFilter() {
    return valueFilter;
  }

  public void setValueFilter(final String valueFilter) {
    this.valueFilter = valueFilter;
  }

  public Integer getResultOffset() {
    return resultOffset;
  }

  public void setResultOffset(final Integer resultOffset) {
    this.resultOffset = resultOffset;
  }

  public Integer getNumResults() {
    return numResults;
  }

  public void setNumResults(final Integer numResults) {
    this.numResults = numResults;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ProcessVariableValuesQueryDto;
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
    return "ProcessVariableValuesQueryDto(processVariableSources="
        + getProcessVariableSources()
        + ", name="
        + getName()
        + ", type="
        + getType()
        + ", valueFilter="
        + getValueFilter()
        + ", resultOffset="
        + getResultOffset()
        + ", numResults="
        + getNumResults()
        + ")";
  }

  public static ProcessVariableValuesQueryDtoBuilder builder() {
    return new ProcessVariableValuesQueryDtoBuilder();
  }

  public static class ProcessVariableValuesQueryDtoBuilder {

    private List<ProcessVariableSourceDto> processVariableSources;
    private String name;
    private VariableType type;
    private String valueFilter;
    private Integer resultOffset;
    private Integer numResults;

    ProcessVariableValuesQueryDtoBuilder() {}

    public ProcessVariableValuesQueryDtoBuilder processVariableSources(
        final List<ProcessVariableSourceDto> processVariableSources) {
      this.processVariableSources = processVariableSources;
      return this;
    }

    public ProcessVariableValuesQueryDtoBuilder name(final String name) {
      this.name = name;
      return this;
    }

    public ProcessVariableValuesQueryDtoBuilder type(final VariableType type) {
      this.type = type;
      return this;
    }

    public ProcessVariableValuesQueryDtoBuilder valueFilter(final String valueFilter) {
      this.valueFilter = valueFilter;
      return this;
    }

    public ProcessVariableValuesQueryDtoBuilder resultOffset(final Integer resultOffset) {
      this.resultOffset = resultOffset;
      return this;
    }

    public ProcessVariableValuesQueryDtoBuilder numResults(final Integer numResults) {
      this.numResults = numResults;
      return this;
    }

    public ProcessVariableValuesQueryDto build() {
      return new ProcessVariableValuesQueryDto(
          processVariableSources, name, type, valueFilter, resultOffset, numResults);
    }

    @Override
    public String toString() {
      return "ProcessVariableValuesQueryDto.ProcessVariableValuesQueryDtoBuilder(processVariableSources="
          + processVariableSources
          + ", name="
          + name
          + ", type="
          + type
          + ", valueFilter="
          + valueFilter
          + ", resultOffset="
          + resultOffset
          + ", numResults="
          + numResults
          + ")";
    }
  }
}
