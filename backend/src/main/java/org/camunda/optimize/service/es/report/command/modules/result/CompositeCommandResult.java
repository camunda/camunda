/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.result;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.DistributedBy;
import org.camunda.optimize.dto.optimize.query.report.single.result.NumberResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.HyperMapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.ReportHyperMapResultDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Collections.singletonList;

@Data
public class CompositeCommandResult {


  private List<GroupByResult> groups = new ArrayList<>();
  private Boolean isComplete = true;

  public void setGroup(GroupByResult groupByResult) {
    this.groups = singletonList(groupByResult);
  }

  @AllArgsConstructor(access = AccessLevel.PROTECTED)
  @Data
  public static class GroupByResult {
    private String key;
    private String label;
    private List<DistributedByResult> distributions;

    public static GroupByResult createResultWithEmptyValue(final String key) {
      return new GroupByResult(key, null, singletonList(DistributedByResult.createResultWithEmptyValue(null)));
    }

    public static GroupByResult createEmptyGroupBy(List<DistributedByResult> distributions) {
      return new GroupByResult(null, null, distributions);
    }

    public static GroupByResult createGroupByResult(final String key, final String label,
                                                    final List<DistributedByResult> distributions) {
      return new GroupByResult(key, label, distributions);
    }

    public static GroupByResult createGroupByResult(final String key, final List<DistributedByResult> distributions) {
      return new GroupByResult(key, null, distributions);
    }

    public String getLabel() {
      return label != null && !label.isEmpty() ? label : key;
    }
  }

  @AllArgsConstructor(access = AccessLevel.PROTECTED)
  @Data
  public static class DistributedByResult {

    private String key;
    private String label;
    private ViewResult viewResult;

    public static DistributedByResult createResultWithEmptyValue(String key) {
      return new DistributedByResult(key, null, new ViewResult(null));
    }

    public static DistributedByResult createEmptyDistributedBy(ViewResult viewResult) {
      return new DistributedByResult(null, null, viewResult);
    }

    public static DistributedByResult createDistributedByResult(String key, String label, ViewResult viewResult) {
      return new DistributedByResult(key, label, viewResult);
    }

    public String getLabel() {
      return label != null && !label.isEmpty() ? label : key;
    }

    public Long getValueAsLong() {
      return this.getViewResult().getValue();
    }

    public MapResultEntryDto getValueAsMapResultEntry() {
      return new MapResultEntryDto(this.key, getValueAsLong(), this.label);
    }
  }

  @AllArgsConstructor
  @Data
  public static class ViewResult {

    private Long value;
  }

  public ReportHyperMapResultDto transformToHyperMap() {
    ReportHyperMapResultDto resultDto = new ReportHyperMapResultDto();
    resultDto.setIsComplete(this.isComplete);

    for (GroupByResult group : groups) {
      List<MapResultEntryDto> distribution = group.distributions.stream()
        .map(DistributedByResult::getValueAsMapResultEntry)
        .collect(Collectors.toList());
      resultDto.getData().add(new HyperMapResultEntryDto(group.getKey(), distribution));
    }
    return resultDto;
  }

  public ReportMapResultDto transformToMap() {
    ReportMapResultDto resultDto = new ReportMapResultDto();
    resultDto.setIsComplete(this.isComplete);
    for (GroupByResult group : groups) {
      final List<DistributedByResult> distributions = group.getDistributions();
      if (distributions.size() == 1) {
        final Long value = distributions.get(0).getValueAsLong();
        resultDto.getData().add(new MapResultEntryDto(group.getKey(), value));
      } else {
        throw new OptimizeRuntimeException(createErrorMessage(ReportMapResultDto.class, DistributedBy.class));
      }
    }
    return resultDto;
  }

  public NumberResultDto transformToNumber() {
    NumberResultDto numberResultDto = new NumberResultDto();
    final List<GroupByResult> groups = this.groups;
    if (groups.size() == 1) {
      final List<DistributedByResult> distributions = groups.get(0).distributions;
      if (distributions.size() == 1) {
        final Long value = distributions.get(0).getViewResult().getValue();
        numberResultDto.setData(value);
        return numberResultDto;
      } else {
        throw new OptimizeRuntimeException(createErrorMessage(NumberResultDto.class, DistributedBy.class));
      }
    } else {
      throw new OptimizeRuntimeException(createErrorMessage(NumberResultDto.class, GroupByResult.class));
    }
  }

  private String createErrorMessage(Class resultClass, Class resultPartClass) {
    return String.format(
      "Could not transform the result of command to a %s since the result has not the right structure. For %s the %s " +
        "result is supposed to contain just one value!",
      resultClass.getSimpleName(),
      resultClass.getSimpleName(),
      resultPartClass.getSimpleName()
    );
  }
}
