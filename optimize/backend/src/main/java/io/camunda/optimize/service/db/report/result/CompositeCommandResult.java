/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.result;

import static io.camunda.optimize.dto.optimize.ReportConstants.GROUP_NONE_KEY;
import static io.camunda.optimize.dto.optimize.ReportConstants.MISSING_VARIABLE_KEY;
import static io.camunda.optimize.dto.optimize.ReportConstants.PAGINATION_DEFAULT_LIMIT;
import static io.camunda.optimize.dto.optimize.ReportConstants.PAGINATION_DEFAULT_OFFSET;
import static io.camunda.optimize.util.SuppressionConstants.UNCHECKED_CAST;
import static java.util.Collections.singletonList;

import io.camunda.optimize.dto.optimize.query.report.CommandEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.single.RawDataInstanceDto;
import io.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.DistributedByType;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.VariableViewPropertyDto;
import io.camunda.optimize.dto.optimize.query.report.single.result.MeasureDto;
import io.camunda.optimize.dto.optimize.query.report.single.result.hyper.HyperMapResultEntryDto;
import io.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import io.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import io.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import io.camunda.optimize.dto.optimize.rest.pagination.PaginationDto;
import io.camunda.optimize.service.db.es.report.result.HyperMapCommandResult;
import io.camunda.optimize.service.db.es.report.result.MapCommandResult;
import io.camunda.optimize.service.db.es.report.result.NumberCommandResult;
import io.camunda.optimize.service.db.es.report.result.RawDataCommandResult;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

public class CompositeCommandResult {

  private final SingleReportDataDto reportDataDto;
  private final ViewProperty viewProperty;

  private ReportSortingDto groupBySorting = new ReportSortingDto();
  private ReportSortingDto distributedBySorting = new ReportSortingDto();
  private boolean isGroupByKeyOfNumericType = false;
  private boolean isDistributedByKeyOfNumericType = false;
  private Supplier<Double> defaultNumberValueSupplier = () -> 0.;

  private List<GroupByResult> groups = new ArrayList<>();

  public CompositeCommandResult(
      final SingleReportDataDto reportDataDto,
      final ViewProperty viewProperty,
      final Double defaultNumberValue) {
    this.reportDataDto = reportDataDto;
    this.viewProperty = viewProperty;
    defaultNumberValueSupplier = () -> defaultNumberValue;
  }

  public CompositeCommandResult(SingleReportDataDto reportDataDto, ViewProperty viewProperty) {
    this.reportDataDto = reportDataDto;
    this.viewProperty = viewProperty;
  }

  public void setGroup(final GroupByResult groupByResult) {
    groups = singletonList(groupByResult);
  }

  public CommandEvaluationResult<List<HyperMapResultEntryDto>> transformToHyperMap() {
    final Map<ViewMeasureIdentifier, List<HyperMapResultEntryDto>> measureDataSets =
        createMeasureMap(ArrayList::new);
    for (final GroupByResult group : groups) {
      final Map<ViewMeasureIdentifier, List<MapResultEntryDto>> distributionsByAggregationType =
          createMeasureMap(ArrayList::new);
      group.distributions.forEach(
          distributedByResult ->
              distributedByResult
                  .getViewResult()
                  .getViewMeasures()
                  .forEach(
                      viewMeasure ->
                          distributionsByAggregationType
                              .get(viewMeasure.getViewMeasureIdentifier())
                              .add(
                                  new MapResultEntryDto(
                                      distributedByResult.getKey(),
                                      viewMeasure.getValue(),
                                      distributedByResult.getLabel()))));

      distributionsByAggregationType.forEach(
          (type, mapResultEntryDtos) -> {
            mapResultEntryDtos.sort(
                getSortingComparator(distributedBySorting, isDistributedByKeyOfNumericType));
            measureDataSets
                .get(type)
                .add(
                    new HyperMapResultEntryDto(
                        group.getKey(), mapResultEntryDtos, group.getLabel()));
          });
    }
    measureDataSets
        .values()
        .forEach(
            hyperMapData ->
                sortHypermapResultData(groupBySorting, isGroupByKeyOfNumericType, hyperMapData));
    return new HyperMapCommandResult(
        measureDataSets.entrySet().stream()
            .map(
                measureDataEntry ->
                    createMeasureDto(measureDataEntry.getKey(), measureDataEntry.getValue()))
            .collect(Collectors.toList()),
        (ProcessReportDataDto) reportDataDto);
  }

  public CommandEvaluationResult<List<MapResultEntryDto>> transformToMap() {
    final Map<ViewMeasureIdentifier, List<MapResultEntryDto>> measureDataSets =
        createMeasureMap(ArrayList::new);
    for (final GroupByResult group : groups) {
      group
          .getDistributions()
          .forEach(
              distributedByResult ->
                  distributedByResult
                      .getViewResult()
                      .getViewMeasures()
                      .forEach(
                          viewMeasure ->
                              measureDataSets
                                  .get(viewMeasure.getViewMeasureIdentifier())
                                  .add(
                                      new MapResultEntryDto(
                                          group.getKey(),
                                          viewMeasure.getValue(),
                                          group.getLabel()))));
    }
    measureDataSets
        .values()
        .forEach(
            mapData ->
                mapData.sort(getSortingComparator(groupBySorting, isGroupByKeyOfNumericType)));

    return new MapCommandResult(
        measureDataSets.entrySet().stream()
            .map(measureEntry -> createMeasureDto(measureEntry.getKey(), measureEntry.getValue()))
            .collect(Collectors.toList()),
        reportDataDto);
  }

  public CommandEvaluationResult<Double> transformToNumber() {
    final Map<ViewMeasureIdentifier, Double> measureDataSets =
        createMeasureMap(defaultNumberValueSupplier);
    if (groups.size() == 1) {
      final List<DistributedByResult> distributions = groups.get(0).distributions;
      if (distributions.size() == 1) {
        final List<ViewMeasure> measures = distributions.get(0).getViewResult().getViewMeasures();
        for (final ViewMeasure viewMeasure : measures) {
          measureDataSets.put(viewMeasure.getViewMeasureIdentifier(), viewMeasure.getValue());
        }
      } else {
        throw new OptimizeRuntimeException(
            createErrorMessage(NumberCommandResult.class, DistributedByType.class));
      }
    }
    return new NumberCommandResult(
        measureDataSets.entrySet().stream()
            .map(measureEntry -> createMeasureDto(measureEntry.getKey(), measureEntry.getValue()))
            .collect(Collectors.toList()),
        reportDataDto);
  }

  private <T> Map<ViewMeasureIdentifier, T> createMeasureMap(
      final Supplier<T> defaultValueSupplier) {
    final Map<ViewMeasureIdentifier, T> measureMap = new LinkedHashMap<>();

    // if this is a frequency or percentage view only null key is expected
    if (ViewProperty.FREQUENCY.equals(viewProperty)
        || ViewProperty.PERCENTAGE.equals(viewProperty)) {
      measureMap.put(new ViewMeasureIdentifier(), defaultValueSupplier.get());
    }

    if (isUserTaskDurationResult()) {
      reportDataDto
          .getConfiguration()
          .getUserTaskDurationTimes()
          .forEach(
              userTaskDurationTime ->
                  reportDataDto
                      .getConfiguration()
                      .getAggregationTypes()
                      .forEach(
                          aggregationType ->
                              measureMap.put(
                                  new ViewMeasureIdentifier(aggregationType, userTaskDurationTime),
                                  defaultValueSupplier.get())));
    } else if (ViewProperty.DURATION.equals(viewProperty) || isNumberVariableView()) {
      // if this is duration view property an entry per aggregationType is expected
      reportDataDto
          .getConfiguration()
          .getAggregationTypes()
          .forEach(
              aggregationType ->
                  measureMap.put(
                      new ViewMeasureIdentifier(aggregationType, null),
                      defaultValueSupplier.get()));
    }

    return measureMap;
  }

  @SuppressWarnings(UNCHECKED_CAST)
  public <T extends RawDataInstanceDto> CommandEvaluationResult<List<T>> transformToRawData() {
    final RawDataCommandResult<T> rawDataCommandResult = new RawDataCommandResult<>(reportDataDto);
    if (groups.isEmpty()) {
      rawDataCommandResult.addMeasure(createMeasureDto(Collections.emptyList()));
      rawDataCommandResult.setPagination(
          new PaginationDto(PAGINATION_DEFAULT_LIMIT, PAGINATION_DEFAULT_OFFSET));
      return rawDataCommandResult;
    } else if (groups.size() == 1) {
      final List<DistributedByResult> distributions = groups.get(0).distributions;
      if (distributions.size() == 1) {
        rawDataCommandResult.addMeasure(
            createMeasureDto((List<T>) distributions.get(0).getViewResult().getRawData()));
        return rawDataCommandResult;
      } else {
        throw new OptimizeRuntimeException(
            createErrorMessage(RawDataCommandResult.class, DistributedByType.class));
      }
    } else {
      throw new OptimizeRuntimeException(
          createErrorMessage(RawDataCommandResult.class, GroupByResult.class));
    }
  }

  private <T> MeasureDto<T> createMeasureDto(final T value) {
    return createMeasureDto(new ViewMeasureIdentifier(), value);
  }

  private <T> MeasureDto<T> createMeasureDto(
      final ViewMeasureIdentifier viewMeasureIdentifier, final T value) {
    return new MeasureDto<>(
        viewProperty,
        viewMeasureIdentifier.getAggregationType(),
        viewMeasureIdentifier.getUserTaskDurationTime(),
        value);
  }

  private boolean isUserTaskDurationResult() {
    return reportDataDto instanceof ProcessReportDataDto
        && ProcessViewEntity.USER_TASK.equals(
            ((ProcessReportDataDto) reportDataDto).getView().getEntity())
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
      final ReportSortingDto sorting, final boolean keyIsOfNumericType) {

    final String sortBy = sorting.getBy().orElse(ReportSortingDto.SORT_BY_KEY);
    final SortOrder sortOrder = sorting.getOrder().orElse(SortOrder.ASC);

    final Function<MapResultEntryDto, Comparable> valueToSortByExtractor;
    switch (sortBy) {
      default:
      case ReportSortingDto.SORT_BY_KEY:
        valueToSortByExtractor =
            entry -> {
              if (entry.getKey().equals(MISSING_VARIABLE_KEY)) {
                return null;
              } else {
                try {
                  return keyIsOfNumericType
                      ? Double.valueOf(entry.getKey())
                      : entry.getKey().toLowerCase(Locale.ENGLISH);
                } catch (final NumberFormatException exception) {
                  throw new OptimizeRuntimeException(
                      "Error sorting numerically for key: " + entry.getKey());
                }
              }
            };
        break;
      case ReportSortingDto.SORT_BY_VALUE:
        valueToSortByExtractor = MapResultEntryDto::getValue;
        break;
      case ReportSortingDto.SORT_BY_LABEL:
        valueToSortByExtractor = entry -> entry.getLabel().toLowerCase(Locale.ENGLISH);
        break;
    }

    final Comparator<MapResultEntryDto> comparator;
    switch (sortOrder) {
      case DESC:
        comparator =
            Comparator.comparing(
                valueToSortByExtractor, Comparator.nullsLast(Comparator.reverseOrder()));
        break;
      default:
      case ASC:
        comparator =
            Comparator.comparing(
                valueToSortByExtractor, Comparator.nullsLast(Comparator.naturalOrder()));
        break;
    }

    return comparator;
  }

  private void sortHypermapResultData(
      final ReportSortingDto sortingDto,
      final boolean keyIsOfNumericType,
      final List<HyperMapResultEntryDto> data) {
    if (data.isEmpty()) {
      return;
    }

    Optional.of(sortingDto)
        .ifPresent(
            sorting -> {
              final String sortBy = sorting.getBy().orElse(ReportSortingDto.SORT_BY_KEY);
              final SortOrder sortOrder = sorting.getOrder().orElse(SortOrder.ASC);

              final Function<HyperMapResultEntryDto, Comparable> valueToSortByExtractor;

              switch (sortBy) {
                default:
                case ReportSortingDto.SORT_BY_KEY:
                  valueToSortByExtractor =
                      entry -> {
                        if (entry.getKey().equals(MISSING_VARIABLE_KEY)) {
                          return null;
                        } else {
                          try {
                            return keyIsOfNumericType
                                ? Double.valueOf(entry.getKey())
                                : entry.getKey().toLowerCase(Locale.ENGLISH);
                          } catch (final NumberFormatException exception) {
                            throw new OptimizeRuntimeException(
                                "Error sorting numerically for key: " + entry.getKey());
                          }
                        }
                      };
                  break;
                case ReportSortingDto.SORT_BY_LABEL:
                  valueToSortByExtractor = entry -> entry.getLabel().toLowerCase(Locale.ENGLISH);
                  break;
              }

              final Comparator<HyperMapResultEntryDto> comparator;
              switch (sortOrder) {
                case DESC:
                  comparator =
                      Comparator.comparing(
                          valueToSortByExtractor, Comparator.nullsLast(Comparator.reverseOrder()));
                  break;
                default:
                case ASC:
                  comparator =
                      Comparator.comparing(
                          valueToSortByExtractor, Comparator.nullsLast(Comparator.naturalOrder()));
                  break;
              }

              data.sort(comparator);
            });
  }

  private String createErrorMessage(final Class<?> resultClass, final Class<?> resultPartClass) {
    return String.format(
        "Could not transform the result of command to a %s since the result has not the right structure. For %s the %s "
            + "result is supposed to contain just one value!",
        resultClass.getSimpleName(), resultClass.getSimpleName(), resultPartClass.getSimpleName());
  }

  public SingleReportDataDto getReportDataDto() {
    return this.reportDataDto;
  }

  public ViewProperty getViewProperty() {
    return this.viewProperty;
  }

  public ReportSortingDto getGroupBySorting() {
    return this.groupBySorting;
  }

  public ReportSortingDto getDistributedBySorting() {
    return this.distributedBySorting;
  }

  public boolean isGroupByKeyOfNumericType() {
    return this.isGroupByKeyOfNumericType;
  }

  public boolean isDistributedByKeyOfNumericType() {
    return this.isDistributedByKeyOfNumericType;
  }

  public Supplier<Double> getDefaultNumberValueSupplier() {
    return this.defaultNumberValueSupplier;
  }

  public List<GroupByResult> getGroups() {
    return this.groups;
  }

  public void setGroupBySorting(ReportSortingDto groupBySorting) {
    this.groupBySorting = groupBySorting;
  }

  public void setDistributedBySorting(ReportSortingDto distributedBySorting) {
    this.distributedBySorting = distributedBySorting;
  }

  public void setGroupByKeyOfNumericType(boolean isGroupByKeyOfNumericType) {
    this.isGroupByKeyOfNumericType = isGroupByKeyOfNumericType;
  }

  public void setDistributedByKeyOfNumericType(boolean isDistributedByKeyOfNumericType) {
    this.isDistributedByKeyOfNumericType = isDistributedByKeyOfNumericType;
  }

  public void setDefaultNumberValueSupplier(Supplier<Double> defaultNumberValueSupplier) {
    this.defaultNumberValueSupplier = defaultNumberValueSupplier;
  }

  public void setGroups(List<GroupByResult> groups) {
    this.groups = groups;
  }

  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof CompositeCommandResult)) {
      return false;
    }
    final CompositeCommandResult other = (CompositeCommandResult) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$reportDataDto = this.getReportDataDto();
    final Object other$reportDataDto = other.getReportDataDto();
    if (this$reportDataDto == null
        ? other$reportDataDto != null
        : !this$reportDataDto.equals(other$reportDataDto)) {
      return false;
    }
    final Object this$viewProperty = this.getViewProperty();
    final Object other$viewProperty = other.getViewProperty();
    if (this$viewProperty == null
        ? other$viewProperty != null
        : !this$viewProperty.equals(other$viewProperty)) {
      return false;
    }
    final Object this$groupBySorting = this.getGroupBySorting();
    final Object other$groupBySorting = other.getGroupBySorting();
    if (this$groupBySorting == null
        ? other$groupBySorting != null
        : !this$groupBySorting.equals(other$groupBySorting)) {
      return false;
    }
    final Object this$distributedBySorting = this.getDistributedBySorting();
    final Object other$distributedBySorting = other.getDistributedBySorting();
    if (this$distributedBySorting == null
        ? other$distributedBySorting != null
        : !this$distributedBySorting.equals(other$distributedBySorting)) {
      return false;
    }
    if (this.isGroupByKeyOfNumericType() != other.isGroupByKeyOfNumericType()) {
      return false;
    }
    if (this.isDistributedByKeyOfNumericType() != other.isDistributedByKeyOfNumericType()) {
      return false;
    }
    final Object this$defaultNumberValueSupplier = this.getDefaultNumberValueSupplier();
    final Object other$defaultNumberValueSupplier = other.getDefaultNumberValueSupplier();
    if (this$defaultNumberValueSupplier == null
        ? other$defaultNumberValueSupplier != null
        : !this$defaultNumberValueSupplier.equals(other$defaultNumberValueSupplier)) {
      return false;
    }
    final Object this$groups = this.getGroups();
    final Object other$groups = other.getGroups();
    if (this$groups == null ? other$groups != null : !this$groups.equals(other$groups)) {
      return false;
    }
    return true;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof CompositeCommandResult;
  }

  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $reportDataDto = this.getReportDataDto();
    result = result * PRIME + ($reportDataDto == null ? 43 : $reportDataDto.hashCode());
    final Object $viewProperty = this.getViewProperty();
    result = result * PRIME + ($viewProperty == null ? 43 : $viewProperty.hashCode());
    final Object $groupBySorting = this.getGroupBySorting();
    result = result * PRIME + ($groupBySorting == null ? 43 : $groupBySorting.hashCode());
    final Object $distributedBySorting = this.getDistributedBySorting();
    result =
        result * PRIME + ($distributedBySorting == null ? 43 : $distributedBySorting.hashCode());
    result = result * PRIME + (this.isGroupByKeyOfNumericType() ? 79 : 97);
    result = result * PRIME + (this.isDistributedByKeyOfNumericType() ? 79 : 97);
    final Object $defaultNumberValueSupplier = this.getDefaultNumberValueSupplier();
    result =
        result * PRIME
            + ($defaultNumberValueSupplier == null ? 43 : $defaultNumberValueSupplier.hashCode());
    final Object $groups = this.getGroups();
    result = result * PRIME + ($groups == null ? 43 : $groups.hashCode());
    return result;
  }

  public String toString() {
    return "CompositeCommandResult(reportDataDto="
        + this.getReportDataDto()
        + ", viewProperty="
        + this.getViewProperty()
        + ", groupBySorting="
        + this.getGroupBySorting()
        + ", distributedBySorting="
        + this.getDistributedBySorting()
        + ", isGroupByKeyOfNumericType="
        + this.isGroupByKeyOfNumericType()
        + ", isDistributedByKeyOfNumericType="
        + this.isDistributedByKeyOfNumericType()
        + ", defaultNumberValueSupplier="
        + this.getDefaultNumberValueSupplier()
        + ", groups="
        + this.getGroups()
        + ")";
  }

  public static class GroupByResult {

    private String key;
    private String label;
    private List<DistributedByResult> distributions;

    protected GroupByResult(String key, String label, List<DistributedByResult> distributions) {
      this.key = key;
      this.label = label;
      this.distributions = distributions;
    }

    public static GroupByResult createGroupByNone(final List<DistributedByResult> distributions) {
      return new GroupByResult(GROUP_NONE_KEY, null, distributions);
    }

    public static GroupByResult createGroupByResult(
        final String key, final String label, final DistributedByResult distribution) {
      return new GroupByResult(key, label, Collections.singletonList(distribution));
    }

    public static GroupByResult createGroupByResult(
        final String key, final List<DistributedByResult> distributions) {
      return new GroupByResult(key, null, distributions);
    }

    public static GroupByResult createGroupByResult(
        final String key, final String label, final List<DistributedByResult> distributions) {
      return new GroupByResult(key, label, distributions);
    }

    public String getLabel() {
      return !StringUtils.isEmpty(label) ? label : key;
    }

    public String getKey() {
      return this.key;
    }

    public List<DistributedByResult> getDistributions() {
      return this.distributions;
    }

    public void setKey(String key) {
      this.key = key;
    }

    public void setLabel(String label) {
      this.label = label;
    }

    public void setDistributions(List<DistributedByResult> distributions) {
      this.distributions = distributions;
    }

    public boolean equals(final Object o) {
      if (o == this) {
        return true;
      }
      if (!(o instanceof GroupByResult)) {
        return false;
      }
      final GroupByResult other = (GroupByResult) o;
      if (!other.canEqual((Object) this)) {
        return false;
      }
      final Object this$key = this.getKey();
      final Object other$key = other.getKey();
      if (this$key == null ? other$key != null : !this$key.equals(other$key)) {
        return false;
      }
      final Object this$label = this.getLabel();
      final Object other$label = other.getLabel();
      if (this$label == null ? other$label != null : !this$label.equals(other$label)) {
        return false;
      }
      final Object this$distributions = this.getDistributions();
      final Object other$distributions = other.getDistributions();
      if (this$distributions == null
          ? other$distributions != null
          : !this$distributions.equals(other$distributions)) {
        return false;
      }
      return true;
    }

    protected boolean canEqual(final Object other) {
      return other instanceof GroupByResult;
    }

    public int hashCode() {
      final int PRIME = 59;
      int result = 1;
      final Object $key = this.getKey();
      result = result * PRIME + ($key == null ? 43 : $key.hashCode());
      final Object $label = this.getLabel();
      result = result * PRIME + ($label == null ? 43 : $label.hashCode());
      final Object $distributions = this.getDistributions();
      result = result * PRIME + ($distributions == null ? 43 : $distributions.hashCode());
      return result;
    }

    public String toString() {
      return "CompositeCommandResult.GroupByResult(key="
          + this.getKey()
          + ", label="
          + this.getLabel()
          + ", distributions="
          + this.getDistributions()
          + ")";
    }
  }

  public static class DistributedByResult {

    private String key;
    private String label;
    private ViewResult viewResult;

    protected DistributedByResult(String key, String label, ViewResult viewResult) {
      this.key = key;
      this.label = label;
      this.viewResult = viewResult;
    }

    public static DistributedByResult createDistributedByNoneResult(final ViewResult viewResult) {
      return new DistributedByResult(null, null, viewResult);
    }

    public static DistributedByResult createDistributedByResult(
        final String key, final String label, final ViewResult viewResult) {
      return new DistributedByResult(key, label, viewResult);
    }

    public String getLabel() {
      return label != null && !label.isEmpty() ? label : key;
    }

    public List<Double> getValuesAsDouble() {
      return getViewResult().getViewMeasures().stream()
          .map(ViewMeasure::getValue)
          .collect(Collectors.toList());
    }

    public List<MapResultEntryDto> getValuesAsMapResultEntries() {
      return getValuesAsDouble().stream()
          .map(value -> new MapResultEntryDto(key, value, label))
          .collect(Collectors.toList());
    }

    public String getKey() {
      return this.key;
    }

    public ViewResult getViewResult() {
      return this.viewResult;
    }

    public void setKey(String key) {
      this.key = key;
    }

    public void setLabel(String label) {
      this.label = label;
    }

    public void setViewResult(ViewResult viewResult) {
      this.viewResult = viewResult;
    }

    public String toString() {
      return "CompositeCommandResult.DistributedByResult(key="
          + this.getKey()
          + ", label="
          + this.getLabel()
          + ", viewResult="
          + this.getViewResult()
          + ")";
    }

    public boolean equals(final Object o) {
      if (o == this) {
        return true;
      }
      if (!(o instanceof DistributedByResult)) {
        return false;
      }
      final DistributedByResult other = (DistributedByResult) o;
      if (!other.canEqual((Object) this)) {
        return false;
      }
      final Object this$key = this.getKey();
      final Object other$key = other.getKey();
      if (this$key == null ? other$key != null : !this$key.equals(other$key)) {
        return false;
      }
      final Object this$label = this.getLabel();
      final Object other$label = other.getLabel();
      if (this$label == null ? other$label != null : !this$label.equals(other$label)) {
        return false;
      }
      final Object this$viewResult = this.getViewResult();
      final Object other$viewResult = other.getViewResult();
      if (this$viewResult == null
          ? other$viewResult != null
          : !this$viewResult.equals(other$viewResult)) {
        return false;
      }
      return true;
    }

    protected boolean canEqual(final Object other) {
      return other instanceof DistributedByResult;
    }

    public int hashCode() {
      final int PRIME = 59;
      int result = 1;
      final Object $key = this.getKey();
      result = result * PRIME + ($key == null ? 43 : $key.hashCode());
      final Object $label = this.getLabel();
      result = result * PRIME + ($label == null ? 43 : $label.hashCode());
      final Object $viewResult = this.getViewResult();
      result = result * PRIME + ($viewResult == null ? 43 : $viewResult.hashCode());
      return result;
    }
  }

  public static class ViewResult {

    private List<ViewMeasure> viewMeasures;
    private List<? extends RawDataInstanceDto> rawData;

    ViewResult(
        final List<ViewMeasure> viewMeasures, final List<? extends RawDataInstanceDto> rawData) {
      this.viewMeasures = viewMeasures;
      this.rawData = rawData;
    }

    public static ViewResultBuilder builder() {
      return new ViewResultBuilder();
    }

    public List<ViewMeasure> getViewMeasures() {
      return this.viewMeasures;
    }

    public List<? extends RawDataInstanceDto> getRawData() {
      return this.rawData;
    }

    public void setViewMeasures(List<ViewMeasure> viewMeasures) {
      this.viewMeasures = viewMeasures;
    }

    public void setRawData(List<? extends RawDataInstanceDto> rawData) {
      this.rawData = rawData;
    }

    public boolean equals(final Object o) {
      if (o == this) {
        return true;
      }
      if (!(o instanceof ViewResult)) {
        return false;
      }
      final ViewResult other = (ViewResult) o;
      if (!other.canEqual((Object) this)) {
        return false;
      }
      final Object this$viewMeasures = this.getViewMeasures();
      final Object other$viewMeasures = other.getViewMeasures();
      if (this$viewMeasures == null
          ? other$viewMeasures != null
          : !this$viewMeasures.equals(other$viewMeasures)) {
        return false;
      }
      final Object this$rawData = this.getRawData();
      final Object other$rawData = other.getRawData();
      if (this$rawData == null ? other$rawData != null : !this$rawData.equals(other$rawData)) {
        return false;
      }
      return true;
    }

    protected boolean canEqual(final Object other) {
      return other instanceof ViewResult;
    }

    public int hashCode() {
      final int PRIME = 59;
      int result = 1;
      final Object $viewMeasures = this.getViewMeasures();
      result = result * PRIME + ($viewMeasures == null ? 43 : $viewMeasures.hashCode());
      final Object $rawData = this.getRawData();
      result = result * PRIME + ($rawData == null ? 43 : $rawData.hashCode());
      return result;
    }

    public String toString() {
      return "CompositeCommandResult.ViewResult(viewMeasures="
          + this.getViewMeasures()
          + ", rawData="
          + this.getRawData()
          + ")";
    }

    public static class ViewResultBuilder {

      private ArrayList<ViewMeasure> viewMeasures;
      private List<? extends RawDataInstanceDto> rawData;

      ViewResultBuilder() {}

      public ViewResultBuilder viewMeasure(final ViewMeasure viewMeasure) {
        if (viewMeasures == null) {
          viewMeasures = new ArrayList<ViewMeasure>();
        }
        viewMeasures.add(viewMeasure);
        return this;
      }

      public ViewResultBuilder viewMeasures(final Collection<? extends ViewMeasure> viewMeasures) {
        if (viewMeasures == null) {
          throw new NullPointerException("viewMeasures cannot be null");
        }
        if (this.viewMeasures == null) {
          this.viewMeasures = new ArrayList<ViewMeasure>();
        }
        this.viewMeasures.addAll(viewMeasures);
        return this;
      }

      public ViewResultBuilder clearViewMeasures() {
        if (viewMeasures != null) {
          viewMeasures.clear();
        }
        return this;
      }

      public ViewResultBuilder rawData(final List<? extends RawDataInstanceDto> rawData) {
        this.rawData = rawData;
        return this;
      }

      public ViewResult build() {
        final List<ViewMeasure> viewMeasures;
        switch (this.viewMeasures == null ? 0 : this.viewMeasures.size()) {
          case 0:
            viewMeasures = Collections.emptyList();
            break;
          case 1:
            viewMeasures = Collections.singletonList(this.viewMeasures.get(0));
            break;
          default:
            viewMeasures =
                Collections.unmodifiableList(new ArrayList<ViewMeasure>(this.viewMeasures));
        }

        return new ViewResult(viewMeasures, rawData);
      }

      @Override
      public String toString() {
        return "CompositeCommandResult.ViewResult.ViewResultBuilder(viewMeasures="
            + viewMeasures
            + ", rawData="
            + rawData
            + ")";
      }
    }
  }

  public static class ViewMeasure {

    private AggregationDto aggregationType;
    private UserTaskDurationTime userTaskDurationTime;
    private Double value;

    ViewMeasure(
        final AggregationDto aggregationType,
        final UserTaskDurationTime userTaskDurationTime,
        final Double value) {
      this.aggregationType = aggregationType;
      this.userTaskDurationTime = userTaskDurationTime;
      this.value = value;
    }

    public ViewMeasureIdentifier getViewMeasureIdentifier() {
      return new ViewMeasureIdentifier(aggregationType, userTaskDurationTime);
    }

    public static ViewMeasureBuilder builder() {
      return new ViewMeasureBuilder();
    }

    public AggregationDto getAggregationType() {
      return this.aggregationType;
    }

    public UserTaskDurationTime getUserTaskDurationTime() {
      return this.userTaskDurationTime;
    }

    public Double getValue() {
      return this.value;
    }

    public void setAggregationType(AggregationDto aggregationType) {
      this.aggregationType = aggregationType;
    }

    public void setUserTaskDurationTime(UserTaskDurationTime userTaskDurationTime) {
      this.userTaskDurationTime = userTaskDurationTime;
    }

    public void setValue(Double value) {
      this.value = value;
    }

    public boolean equals(final Object o) {
      if (o == this) {
        return true;
      }
      if (!(o instanceof ViewMeasure)) {
        return false;
      }
      final ViewMeasure other = (ViewMeasure) o;
      if (!other.canEqual((Object) this)) {
        return false;
      }
      final Object this$aggregationType = this.getAggregationType();
      final Object other$aggregationType = other.getAggregationType();
      if (this$aggregationType == null
          ? other$aggregationType != null
          : !this$aggregationType.equals(other$aggregationType)) {
        return false;
      }
      final Object this$userTaskDurationTime = this.getUserTaskDurationTime();
      final Object other$userTaskDurationTime = other.getUserTaskDurationTime();
      if (this$userTaskDurationTime == null
          ? other$userTaskDurationTime != null
          : !this$userTaskDurationTime.equals(other$userTaskDurationTime)) {
        return false;
      }
      final Object this$value = this.getValue();
      final Object other$value = other.getValue();
      if (this$value == null ? other$value != null : !this$value.equals(other$value)) {
        return false;
      }
      return true;
    }

    protected boolean canEqual(final Object other) {
      return other instanceof ViewMeasure;
    }

    public int hashCode() {
      final int PRIME = 59;
      int result = 1;
      final Object $aggregationType = this.getAggregationType();
      result = result * PRIME + ($aggregationType == null ? 43 : $aggregationType.hashCode());
      final Object $userTaskDurationTime = this.getUserTaskDurationTime();
      result =
          result * PRIME + ($userTaskDurationTime == null ? 43 : $userTaskDurationTime.hashCode());
      final Object $value = this.getValue();
      result = result * PRIME + ($value == null ? 43 : $value.hashCode());
      return result;
    }

    public String toString() {
      return "CompositeCommandResult.ViewMeasure(aggregationType="
          + this.getAggregationType()
          + ", userTaskDurationTime="
          + this.getUserTaskDurationTime()
          + ", value="
          + this.getValue()
          + ")";
    }

    public static class ViewMeasureBuilder {

      private AggregationDto aggregationType;
      private UserTaskDurationTime userTaskDurationTime;
      private Double value;

      ViewMeasureBuilder() {}

      public ViewMeasureBuilder aggregationType(final AggregationDto aggregationType) {
        this.aggregationType = aggregationType;
        return this;
      }

      public ViewMeasureBuilder userTaskDurationTime(
          final UserTaskDurationTime userTaskDurationTime) {
        this.userTaskDurationTime = userTaskDurationTime;
        return this;
      }

      public ViewMeasureBuilder value(final Double value) {
        this.value = value;
        return this;
      }

      public ViewMeasure build() {
        return new ViewMeasure(aggregationType, userTaskDurationTime, value);
      }

      @Override
      public String toString() {
        return "CompositeCommandResult.ViewMeasure.ViewMeasureBuilder(aggregationType="
            + aggregationType
            + ", userTaskDurationTime="
            + userTaskDurationTime
            + ", value="
            + value
            + ")";
      }
    }
  }

  public static class ViewMeasureIdentifier {

    private AggregationDto aggregationType;
    private UserTaskDurationTime userTaskDurationTime;

    public ViewMeasureIdentifier(final AggregationDto aggregationDto) {
      aggregationType = aggregationDto;
    }

    public ViewMeasureIdentifier(
        AggregationDto aggregationType, UserTaskDurationTime userTaskDurationTime) {
      this.aggregationType = aggregationType;
      this.userTaskDurationTime = userTaskDurationTime;
    }

    public ViewMeasureIdentifier() {}

    public AggregationDto getAggregationType() {
      return this.aggregationType;
    }

    public UserTaskDurationTime getUserTaskDurationTime() {
      return this.userTaskDurationTime;
    }

    public void setAggregationType(AggregationDto aggregationType) {
      this.aggregationType = aggregationType;
    }

    public void setUserTaskDurationTime(UserTaskDurationTime userTaskDurationTime) {
      this.userTaskDurationTime = userTaskDurationTime;
    }

    public boolean equals(final Object o) {
      if (o == this) {
        return true;
      }
      if (!(o instanceof ViewMeasureIdentifier)) {
        return false;
      }
      final ViewMeasureIdentifier other = (ViewMeasureIdentifier) o;
      if (!other.canEqual((Object) this)) {
        return false;
      }
      final Object this$aggregationType = this.getAggregationType();
      final Object other$aggregationType = other.getAggregationType();
      if (this$aggregationType == null
          ? other$aggregationType != null
          : !this$aggregationType.equals(other$aggregationType)) {
        return false;
      }
      final Object this$userTaskDurationTime = this.getUserTaskDurationTime();
      final Object other$userTaskDurationTime = other.getUserTaskDurationTime();
      if (this$userTaskDurationTime == null
          ? other$userTaskDurationTime != null
          : !this$userTaskDurationTime.equals(other$userTaskDurationTime)) {
        return false;
      }
      return true;
    }

    protected boolean canEqual(final Object other) {
      return other instanceof ViewMeasureIdentifier;
    }

    public int hashCode() {
      final int PRIME = 59;
      int result = 1;
      final Object $aggregationType = this.getAggregationType();
      result = result * PRIME + ($aggregationType == null ? 43 : $aggregationType.hashCode());
      final Object $userTaskDurationTime = this.getUserTaskDurationTime();
      result =
          result * PRIME + ($userTaskDurationTime == null ? 43 : $userTaskDurationTime.hashCode());
      return result;
    }

    public String toString() {
      return "CompositeCommandResult.ViewMeasureIdentifier(aggregationType="
          + this.getAggregationType()
          + ", userTaskDurationTime="
          + this.getUserTaskDurationTime()
          + ")";
    }
  }
}
