/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.result;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;
import org.camunda.optimize.dto.optimize.query.report.CommandEvaluationResult;
import org.camunda.optimize.dto.optimize.query.report.single.RawDataInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.DistributedByType;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.result.MeasureDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.HyperMapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.result.HyperMapCommandResult;
import org.camunda.optimize.service.es.report.result.MapCommandResult;
import org.camunda.optimize.service.es.report.result.NumberCommandResult;
import org.camunda.optimize.service.es.report.result.RawDataCommandResult;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Collections.singletonList;
import static org.camunda.optimize.dto.optimize.ReportConstants.MISSING_VARIABLE_KEY;
import static org.camunda.optimize.util.SuppressionConstants.UNCHECKED_CAST;

@Data
@Accessors(chain = true)
public class CompositeCommandResult {

  private final SingleReportDataDto reportDataDto;
  private ReportSortingDto groupBySorting = new ReportSortingDto();
  private ReportSortingDto distributedBySorting = new ReportSortingDto();
  private boolean isGroupByKeyOfNumericType = false;
  private boolean isDistributedByKeyOfNumericType = false;
  private ViewProperty viewProperty;

  private List<GroupByResult> groups = new ArrayList<>();

  public void setGroup(GroupByResult groupByResult) {
    this.groups = singletonList(groupByResult);
  }

  public CommandEvaluationResult<List<HyperMapResultEntryDto>> transformToHyperMap() {
    List<HyperMapResultEntryDto> hyperMapData = new ArrayList<>();
    for (GroupByResult group : groups) {
      List<MapResultEntryDto> distribution = group.distributions.stream()
        .map(DistributedByResult::getValueAsMapResultEntry)
        .sorted(getSortingComparator(distributedBySorting, isDistributedByKeyOfNumericType))
        .collect(Collectors.toList());
      hyperMapData.add(new HyperMapResultEntryDto(group.getKey(), distribution, group.getLabel()));
    }
    sortHypermapResultData(groupBySorting, isGroupByKeyOfNumericType, hyperMapData);
    return new HyperMapCommandResult(
      Collections.singletonList(createMeasureDto(hyperMapData)),
      (ProcessReportDataDto) reportDataDto
    );
  }

  public CommandEvaluationResult<List<MapResultEntryDto>> transformToMap() {
    List<MapResultEntryDto> mapData = new ArrayList<>();
    for (GroupByResult group : groups) {
      final List<DistributedByResult> distributions = group.getDistributions();
      if (distributions.size() == 1) {
        final Double value = distributions.get(0).getValueAsDouble();
        mapData.add(new MapResultEntryDto(group.getKey(), value, group.getLabel()));
      } else {
        throw new OptimizeRuntimeException(createErrorMessage(MapCommandResult.class, DistributedByType.class));
      }
    }
    mapData.sort(getSortingComparator(groupBySorting, isGroupByKeyOfNumericType));
    return new MapCommandResult(Collections.singletonList(createMeasureDto(mapData)), reportDataDto);
  }

  public CommandEvaluationResult<Double> transformToNumber() {
    final NumberCommandResult numberResultDto = new NumberCommandResult(reportDataDto);
    if (groups.isEmpty()) {
      numberResultDto.addMeasure(createMeasureDto(0.));
      return numberResultDto;
    } else if (groups.size() == 1) {
      final List<DistributedByResult> distributions = groups.get(0).distributions;
      if (distributions.size() == 1) {
        final Double value = distributions.get(0).getViewResult().getNumber();
        numberResultDto.addMeasure(createMeasureDto(value));
        return numberResultDto;
      } else {
        throw new OptimizeRuntimeException(createErrorMessage(NumberCommandResult.class, DistributedByType.class));
      }
    } else {
      throw new OptimizeRuntimeException(createErrorMessage(NumberCommandResult.class, GroupByResult.class));
    }
  }

  @SuppressWarnings(UNCHECKED_CAST)
  public <T extends RawDataInstanceDto> CommandEvaluationResult<List<T>> transformToRawData() {
    final RawDataCommandResult<T> rawDataCommandResult = new RawDataCommandResult<>(reportDataDto);
    if (groups.isEmpty()) {
      rawDataCommandResult.addMeasure(createMeasureDto(Collections.emptyList()));
      return rawDataCommandResult;
    } else if (groups.size() == 1) {
      final List<DistributedByResult> distributions = groups.get(0).distributions;
      if (distributions.size() == 1) {
        rawDataCommandResult.addMeasure(
          createMeasureDto((List<T>) distributions.get(0).getViewResult().getRawData())
        );
        return rawDataCommandResult;
      } else {
        throw new OptimizeRuntimeException(createErrorMessage(RawDataCommandResult.class, DistributedByType.class));
      }
    } else {
      throw new OptimizeRuntimeException(createErrorMessage(RawDataCommandResult.class, GroupByResult.class));
    }
  }

  private <T> MeasureDto<T> createMeasureDto(final T value) {
    return new MeasureDto<>(viewProperty, getAggregationType(), getUserTaskDurationTime(), value);
  }

  private AggregationType getAggregationType() {
    return ViewProperty.DURATION.equals(viewProperty)
      ? reportDataDto.getConfiguration().getAggregationTypes().get(0)
      : null;
  }

  private UserTaskDurationTime getUserTaskDurationTime() {
    if (reportDataDto instanceof ProcessReportDataDto) {
      return ProcessViewEntity.USER_TASK.equals(((ProcessReportDataDto) reportDataDto).getView().getEntity())
        && ViewProperty.DURATION.equals(viewProperty)
        ? reportDataDto.getConfiguration().getUserTaskDurationTimes().get(0)
        : null;
    } else {
      return null;
    }
  }

  private Comparator<MapResultEntryDto> getSortingComparator(
    final ReportSortingDto sorting,
    final boolean keyIsOfNumericType) {

    final String sortBy = sorting.getBy().orElse(ReportSortingDto.SORT_BY_KEY);
    final SortOrder sortOrder = sorting.getOrder().orElse(SortOrder.ASC);

    final Function<MapResultEntryDto, Comparable> valueToSortByExtractor;
    switch (sortBy) {
      default:
      case ReportSortingDto.SORT_BY_KEY:
        valueToSortByExtractor = entry -> {
          if (entry.getKey().equals(MISSING_VARIABLE_KEY)) {
            return null;
          } else {
            return keyIsOfNumericType ? Double.valueOf(entry.getKey()) : entry.getKey().toLowerCase();
          }
        };
        break;
      case ReportSortingDto.SORT_BY_VALUE:
        valueToSortByExtractor = MapResultEntryDto::getValue;
        break;
      case ReportSortingDto.SORT_BY_LABEL:
        valueToSortByExtractor = entry -> entry.getLabel().toLowerCase();
        break;
    }

    final Comparator<MapResultEntryDto> comparator;
    switch (sortOrder) {
      case DESC:
        comparator = Comparator.comparing(valueToSortByExtractor, Comparator.nullsLast(Comparator.reverseOrder()));
        break;
      default:
      case ASC:
        comparator = Comparator.comparing(valueToSortByExtractor, Comparator.nullsLast(Comparator.naturalOrder()));
        break;
    }

    return comparator;
  }

  private void sortHypermapResultData(final ReportSortingDto sortingDto,
                                      final boolean keyIsOfNumericType,
                                      final List<HyperMapResultEntryDto> data) {
    if (data.isEmpty()) {
      return;
    }

    Optional.of(sortingDto).ifPresent(
      sorting -> {
        final String sortBy = sorting.getBy().orElse(ReportSortingDto.SORT_BY_KEY);
        final SortOrder sortOrder = sorting.getOrder().orElse(SortOrder.ASC);

        final Function<HyperMapResultEntryDto, Comparable> valueToSortByExtractor;

        switch (sortBy) {
          default:
          case ReportSortingDto.SORT_BY_KEY:
            valueToSortByExtractor = entry -> {
              if (entry.getKey().equals(MISSING_VARIABLE_KEY)) {
                return null;
              } else {
                return keyIsOfNumericType ? Double.valueOf(entry.getKey()) : entry.getKey().toLowerCase();
              }
            };
            break;
          case ReportSortingDto.SORT_BY_LABEL:
            valueToSortByExtractor = entry -> entry.getLabel().toLowerCase();
            break;
        }

        final Comparator<HyperMapResultEntryDto> comparator;
        switch (sortOrder) {
          case DESC:
            comparator = Comparator.comparing(valueToSortByExtractor, Comparator.nullsLast(Comparator.reverseOrder()));
            break;
          default:
          case ASC:
            comparator = Comparator.comparing(valueToSortByExtractor, Comparator.nullsLast(Comparator.naturalOrder()));
            break;
        }

        data.sort(comparator);
      }
    );
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

  @AllArgsConstructor(access = AccessLevel.PROTECTED)
  @Data
  public static class GroupByResult {
    private String key;
    private String label;
    private List<DistributedByResult> distributions;

    public static GroupByResult createResultWithEmptyDistributedBy(final String key) {
      return new GroupByResult(key, null, singletonList(DistributedByResult.createResultWithEmptyValue(key)));
    }

    public static GroupByResult createResultWithEmptyDistributedBy(final String key,
                                                                   final ExecutionContext<ProcessReportDataDto> context) {
      return new GroupByResult(
        key,
        null,
        DistributedByResult.createEmptyDistributedByResultsForAllPossibleKeys(context)
      );
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
      return !StringUtils.isEmpty(label) ? label : key;
    }
  }

  @AllArgsConstructor(access = AccessLevel.PROTECTED)
  @Data
  @EqualsAndHashCode
  public static class DistributedByResult {

    private String key;
    private String label;
    private ViewResult viewResult;

    public static DistributedByResult createResultWithEmptyValue(String key) {
      return new DistributedByResult(key, null, new ViewResult());
    }

    public static DistributedByResult createResultWithZeroValue(String key) {
      return createResultWithZeroValue(key, null);
    }

    public static DistributedByResult createResultWithZeroValue(String key, String label) {
      ViewResult viewResult = new ViewResult();
      viewResult.setNumber(0.0);
      return new DistributedByResult(key, label, viewResult);
    }

    public static DistributedByResult createResultWithEmptyValue(String key, String label) {
      return new DistributedByResult(key, label, new ViewResult());
    }

    public static DistributedByResult createEmptyDistributedBy(ViewResult viewResult) {
      return new DistributedByResult(null, null, viewResult);
    }

    public static DistributedByResult createDistributedByResult(String key, String label, ViewResult viewResult) {
      return new DistributedByResult(key, label, viewResult);
    }

    public static List<DistributedByResult> createEmptyDistributedByResultsForAllPossibleKeys(
      final ExecutionContext<ProcessReportDataDto> context) {
      return context.getAllDistributedByKeysAndLabels()
        .entrySet()
        .stream()
        .map(entry -> createResultWithEmptyValue(entry.getKey(), entry.getValue()))
        .collect(Collectors.toList());
    }

    public String getLabel() {
      return label != null && !label.isEmpty() ? label : key;
    }

    public Double getValueAsDouble() {
      return this.getViewResult().getNumber();
    }

    public MapResultEntryDto getValueAsMapResultEntry() {
      return new MapResultEntryDto(this.key, getValueAsDouble(), this.label);
    }
  }

  @NoArgsConstructor
  @Accessors(chain = true)
  @Data
  public static class ViewResult {
    private Double number;
    private List<? extends RawDataInstanceDto> rawData;
  }
}
