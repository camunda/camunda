/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.result;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Singular;
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
import org.camunda.optimize.dto.optimize.query.report.single.process.view.VariableViewPropertyDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.MeasureDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.HyperMapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.rest.pagination.PaginationDto;
import org.camunda.optimize.service.es.report.result.HyperMapCommandResult;
import org.camunda.optimize.service.es.report.result.MapCommandResult;
import org.camunda.optimize.service.es.report.result.NumberCommandResult;
import org.camunda.optimize.service.es.report.result.RawDataCommandResult;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.util.Collections.singletonList;
import static org.camunda.optimize.dto.optimize.ReportConstants.GROUP_NONE_KEY;
import static org.camunda.optimize.dto.optimize.ReportConstants.MISSING_VARIABLE_KEY;
import static org.camunda.optimize.dto.optimize.ReportConstants.PAGINATION_DEFAULT_LIMIT;
import static org.camunda.optimize.dto.optimize.ReportConstants.PAGINATION_DEFAULT_OFFSET;
import static org.camunda.optimize.util.SuppressionConstants.UNCHECKED_CAST;

@Data
@RequiredArgsConstructor
@Accessors(chain = true)
public class CompositeCommandResult {
  private final SingleReportDataDto reportDataDto;
  private final ViewProperty viewProperty;

  private ReportSortingDto groupBySorting = new ReportSortingDto();
  private ReportSortingDto distributedBySorting = new ReportSortingDto();
  private boolean isGroupByKeyOfNumericType = false;
  private boolean isDistributedByKeyOfNumericType = false;
  private Supplier<Double> defaultNumberValueSupplier = () -> 0.;

  private List<GroupByResult> groups = new ArrayList<>();

  public CompositeCommandResult(final SingleReportDataDto reportDataDto,
                                final ViewProperty viewProperty,
                                final Double defaultNumberValue) {
    this.reportDataDto = reportDataDto;
    this.viewProperty = viewProperty;
    this.defaultNumberValueSupplier = () -> defaultNumberValue;
  }

  public void setGroup(final GroupByResult groupByResult) {
    this.groups = singletonList(groupByResult);
  }

  public CommandEvaluationResult<List<HyperMapResultEntryDto>> transformToHyperMap() {
    final Map<ViewMeasureIdentifier, List<HyperMapResultEntryDto>> measureDataSets = createMeasureMap(ArrayList::new);
    for (GroupByResult group : groups) {
      final Map<ViewMeasureIdentifier, List<MapResultEntryDto>> distributionsByAggregationType =
        createMeasureMap(ArrayList::new);
      group.distributions.forEach(distributedByResult -> distributedByResult.getViewResult().getViewMeasures()
        .forEach(viewMeasure -> distributionsByAggregationType.get(viewMeasure.getViewMeasureIdentifier())
          .add(new MapResultEntryDto(
            distributedByResult.getKey(), viewMeasure.getValue(), distributedByResult.getLabel()
          ))
        ));

      distributionsByAggregationType.forEach((type, mapResultEntryDtos) -> {
        mapResultEntryDtos.sort(getSortingComparator(distributedBySorting, isDistributedByKeyOfNumericType));
        measureDataSets.get(type).add(new HyperMapResultEntryDto(group.getKey(), mapResultEntryDtos, group.getLabel()));
      });
    }
    measureDataSets.values().forEach(
      hyperMapData -> sortHypermapResultData(groupBySorting, isGroupByKeyOfNumericType, hyperMapData)
    );
    return new HyperMapCommandResult(
      measureDataSets.entrySet().stream()
        .map(measureDataEntry -> createMeasureDto(measureDataEntry.getKey(), measureDataEntry.getValue()))
        .collect(Collectors.toList()),
      (ProcessReportDataDto) reportDataDto
    );
  }

  public CommandEvaluationResult<List<MapResultEntryDto>> transformToMap() {
    final Map<ViewMeasureIdentifier, List<MapResultEntryDto>> measureDataSets = createMeasureMap(ArrayList::new);
    for (GroupByResult group : groups) {
      group.getDistributions().forEach(distributedByResult -> distributedByResult.getViewResult().getViewMeasures()
        .forEach(viewMeasure -> measureDataSets.get(viewMeasure.getViewMeasureIdentifier()).add(
          new MapResultEntryDto(group.getKey(), viewMeasure.getValue(), group.getLabel())
        ))
      );
    }
    measureDataSets.values().forEach(
      mapData -> mapData.sort(getSortingComparator(groupBySorting, isGroupByKeyOfNumericType))
    );

    return new MapCommandResult(
      measureDataSets.entrySet()
        .stream()
        .map(measureEntry -> createMeasureDto(measureEntry.getKey(), measureEntry.getValue()))
        .collect(Collectors.toList()),
      reportDataDto
    );
  }

  public CommandEvaluationResult<Double> transformToNumber() {
    final Map<ViewMeasureIdentifier, Double> measureDataSets = createMeasureMap(defaultNumberValueSupplier);
    if (groups.size() == 1) {
      final List<DistributedByResult> distributions = groups.get(0).distributions;
      if (distributions.size() == 1) {
        final List<ViewMeasure> measures = distributions.get(0).getViewResult().getViewMeasures();
        for (final ViewMeasure viewMeasure : measures) {
          measureDataSets.put(viewMeasure.getViewMeasureIdentifier(), viewMeasure.getValue());
        }
      } else {
        throw new OptimizeRuntimeException(createErrorMessage(NumberCommandResult.class, DistributedByType.class));
      }
    }
    return new NumberCommandResult(
      measureDataSets.entrySet()
        .stream()
        .map(measureEntry -> createMeasureDto(measureEntry.getKey(), measureEntry.getValue()))
        .collect(Collectors.toList()),
      reportDataDto
    );
  }

  private <T> Map<ViewMeasureIdentifier, T> createMeasureMap(final Supplier<T> defaultValueSupplier) {
    final Map<ViewMeasureIdentifier, T> measureMap = new LinkedHashMap<>();

    // if this is a frequency view only null key is expected
    if (ViewProperty.FREQUENCY.equals(viewProperty) || ViewProperty.PERCENTAGE.equals(viewProperty)) {
      measureMap.put(new ViewMeasureIdentifier(), defaultValueSupplier.get());
    }

    if (isUserTaskDurationResult()) {
      reportDataDto.getConfiguration().getUserTaskDurationTimes()
        .forEach(userTaskDurationTime -> reportDataDto.getConfiguration().getAggregationTypes()
          .forEach(aggregationType -> measureMap.put(
            new ViewMeasureIdentifier(aggregationType, userTaskDurationTime), defaultValueSupplier.get())
          ));
    } else if (ViewProperty.DURATION.equals(viewProperty) || isNumberVariableView()) {
      // if this is duration view property an entry per aggregationType is expected
      reportDataDto.getConfiguration().getAggregationTypes()
        .forEach(aggregationType -> measureMap.put(
          new ViewMeasureIdentifier(aggregationType, null), defaultValueSupplier.get()
        ));
    }

    return measureMap;
  }

  @SuppressWarnings(UNCHECKED_CAST)
  public <T extends RawDataInstanceDto> CommandEvaluationResult<List<T>> transformToRawData() {
    final RawDataCommandResult<T> rawDataCommandResult = new RawDataCommandResult<>(reportDataDto);
    if (groups.isEmpty()) {
      rawDataCommandResult.addMeasure(createMeasureDto(Collections.emptyList()));
      rawDataCommandResult.setPagination(new PaginationDto(PAGINATION_DEFAULT_LIMIT, PAGINATION_DEFAULT_OFFSET));
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
    return createMeasureDto(new ViewMeasureIdentifier(), value);
  }

  private <T> MeasureDto<T> createMeasureDto(final ViewMeasureIdentifier viewMeasureIdentifier, final T value) {
    return new MeasureDto<>(
      viewProperty, viewMeasureIdentifier.getAggregationType(), viewMeasureIdentifier.getUserTaskDurationTime(), value
    );
  }

  private boolean isUserTaskDurationResult() {
    return reportDataDto instanceof ProcessReportDataDto
      && ProcessViewEntity.USER_TASK.equals(((ProcessReportDataDto) reportDataDto).getView().getEntity())
      && ViewProperty.DURATION.equals(viewProperty);
  }

  private boolean isNumberVariableView() {
    return Optional.ofNullable(viewProperty)
      .flatMap(value -> value.getViewPropertyDtoIfOfType(VariableViewPropertyDto.class))
      .map(VariableViewPropertyDto::getType)
      .filter(propertyType -> VariableType.getNumericTypes().contains(propertyType))
      .isPresent();
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

  private String createErrorMessage(final Class<?> resultClass, final Class<?> resultPartClass) {
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

    public static GroupByResult createGroupByNone(final List<DistributedByResult> distributions) {
      return new GroupByResult(GROUP_NONE_KEY, null, distributions);
    }

    public static GroupByResult createGroupByResult(final String key,
                                                    final String label,
                                                    final DistributedByResult distribution) {
      return new GroupByResult(key, label, Collections.singletonList(distribution));
    }

    public static GroupByResult createGroupByResult(final String key, final List<DistributedByResult> distributions) {
      return new GroupByResult(key, null, distributions);
    }

    public static GroupByResult createGroupByResult(final String key,
                                                    final String label,
                                                    final List<DistributedByResult> distributions) {
      return new GroupByResult(key, label, distributions);
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

    public static DistributedByResult createDistributedByNoneResult(ViewResult viewResult) {
      return new DistributedByResult(null, null, viewResult);
    }

    public static DistributedByResult createDistributedByResult(String key, String label, ViewResult viewResult) {
      return new DistributedByResult(key, label, viewResult);
    }

    public String getLabel() {
      return label != null && !label.isEmpty() ? label : key;
    }

    public List<Double> getValuesAsDouble() {
      return this.getViewResult().getViewMeasures().stream().map(ViewMeasure::getValue).collect(Collectors.toList());
    }

    public List<MapResultEntryDto> getValuesAsMapResultEntries() {
      return getValuesAsDouble().stream()
        .map(value -> new MapResultEntryDto(this.key, value, this.label))
        .collect(Collectors.toList());
    }
  }

  @Builder
  @Data
  public static class ViewResult {
    @Singular
    private List<ViewMeasure> viewMeasures;
    private List<? extends RawDataInstanceDto> rawData;
  }

  @Builder
  @Data
  public static class ViewMeasure {
    private AggregationType aggregationType;
    private UserTaskDurationTime userTaskDurationTime;
    private Double value;

    public ViewMeasureIdentifier getViewMeasureIdentifier() {
      return new ViewMeasureIdentifier(aggregationType, userTaskDurationTime);
    }
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class ViewMeasureIdentifier {
    private AggregationType aggregationType;
    private UserTaskDurationTime userTaskDurationTime;
  }
}
