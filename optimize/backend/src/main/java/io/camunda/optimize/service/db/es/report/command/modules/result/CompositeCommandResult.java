/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.command.modules.result;

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

  public CompositeCommandResult(
      final SingleReportDataDto reportDataDto, final ViewProperty viewProperty) {
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
    return reportDataDto;
  }

  public ViewProperty getViewProperty() {
    return viewProperty;
  }

  public ReportSortingDto getGroupBySorting() {
    return groupBySorting;
  }

  public CompositeCommandResult setGroupBySorting(final ReportSortingDto groupBySorting) {
    this.groupBySorting = groupBySorting;
    return this;
  }

  public ReportSortingDto getDistributedBySorting() {
    return distributedBySorting;
  }

  public CompositeCommandResult setDistributedBySorting(
      final ReportSortingDto distributedBySorting) {
    this.distributedBySorting = distributedBySorting;
    return this;
  }

  public boolean isGroupByKeyOfNumericType() {
    return isGroupByKeyOfNumericType;
  }

  public CompositeCommandResult setGroupByKeyOfNumericType(
      final boolean isGroupByKeyOfNumericType) {
    this.isGroupByKeyOfNumericType = isGroupByKeyOfNumericType;
    return this;
  }

  public boolean isDistributedByKeyOfNumericType() {
    return isDistributedByKeyOfNumericType;
  }

  public CompositeCommandResult setDistributedByKeyOfNumericType(
      final boolean isDistributedByKeyOfNumericType) {
    this.isDistributedByKeyOfNumericType = isDistributedByKeyOfNumericType;
    return this;
  }

  public Supplier<Double> getDefaultNumberValueSupplier() {
    return defaultNumberValueSupplier;
  }

  public CompositeCommandResult setDefaultNumberValueSupplier(
      final Supplier<Double> defaultNumberValueSupplier) {
    this.defaultNumberValueSupplier = defaultNumberValueSupplier;
    return this;
  }

  public List<GroupByResult> getGroups() {
    return groups;
  }

  public CompositeCommandResult setGroups(final List<GroupByResult> groups) {
    this.groups = groups;
    return this;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof CompositeCommandResult;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $reportDataDto = getReportDataDto();
    result = result * PRIME + ($reportDataDto == null ? 43 : $reportDataDto.hashCode());
    final Object $viewProperty = getViewProperty();
    result = result * PRIME + ($viewProperty == null ? 43 : $viewProperty.hashCode());
    final Object $groupBySorting = getGroupBySorting();
    result = result * PRIME + ($groupBySorting == null ? 43 : $groupBySorting.hashCode());
    final Object $distributedBySorting = getDistributedBySorting();
    result =
        result * PRIME + ($distributedBySorting == null ? 43 : $distributedBySorting.hashCode());
    result = result * PRIME + (isGroupByKeyOfNumericType() ? 79 : 97);
    result = result * PRIME + (isDistributedByKeyOfNumericType() ? 79 : 97);
    final Object $defaultNumberValueSupplier = getDefaultNumberValueSupplier();
    result =
        result * PRIME
            + ($defaultNumberValueSupplier == null ? 43 : $defaultNumberValueSupplier.hashCode());
    final Object $groups = getGroups();
    result = result * PRIME + ($groups == null ? 43 : $groups.hashCode());
    return result;
  }

  @Override
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
    final Object this$reportDataDto = getReportDataDto();
    final Object other$reportDataDto = other.getReportDataDto();
    if (this$reportDataDto == null
        ? other$reportDataDto != null
        : !this$reportDataDto.equals(other$reportDataDto)) {
      return false;
    }
    final Object this$viewProperty = getViewProperty();
    final Object other$viewProperty = other.getViewProperty();
    if (this$viewProperty == null
        ? other$viewProperty != null
        : !this$viewProperty.equals(other$viewProperty)) {
      return false;
    }
    final Object this$groupBySorting = getGroupBySorting();
    final Object other$groupBySorting = other.getGroupBySorting();
    if (this$groupBySorting == null
        ? other$groupBySorting != null
        : !this$groupBySorting.equals(other$groupBySorting)) {
      return false;
    }
    final Object this$distributedBySorting = getDistributedBySorting();
    final Object other$distributedBySorting = other.getDistributedBySorting();
    if (this$distributedBySorting == null
        ? other$distributedBySorting != null
        : !this$distributedBySorting.equals(other$distributedBySorting)) {
      return false;
    }
    if (isGroupByKeyOfNumericType() != other.isGroupByKeyOfNumericType()) {
      return false;
    }
    if (isDistributedByKeyOfNumericType() != other.isDistributedByKeyOfNumericType()) {
      return false;
    }
    final Object this$defaultNumberValueSupplier = getDefaultNumberValueSupplier();
    final Object other$defaultNumberValueSupplier = other.getDefaultNumberValueSupplier();
    if (this$defaultNumberValueSupplier == null
        ? other$defaultNumberValueSupplier != null
        : !this$defaultNumberValueSupplier.equals(other$defaultNumberValueSupplier)) {
      return false;
    }
    final Object this$groups = getGroups();
    final Object other$groups = other.getGroups();
    if (this$groups == null ? other$groups != null : !this$groups.equals(other$groups)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "CompositeCommandResult(reportDataDto="
        + getReportDataDto()
        + ", viewProperty="
        + getViewProperty()
        + ", groupBySorting="
        + getGroupBySorting()
        + ", distributedBySorting="
        + getDistributedBySorting()
        + ", isGroupByKeyOfNumericType="
        + isGroupByKeyOfNumericType()
        + ", isDistributedByKeyOfNumericType="
        + isDistributedByKeyOfNumericType()
        + ", defaultNumberValueSupplier="
        + getDefaultNumberValueSupplier()
        + ", groups="
        + getGroups()
        + ")";
  }

  public static class GroupByResult {

    private String key;
    private String label;
    private List<DistributedByResult> distributions;

    protected GroupByResult(
        final String key, final String label, final List<DistributedByResult> distributions) {
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

    public GroupByResult setLabel(final String label) {
      this.label = label;
      return this;
    }

    public String getKey() {
      return key;
    }

    public GroupByResult setKey(final String key) {
      this.key = key;
      return this;
    }

    public List<DistributedByResult> getDistributions() {
      return distributions;
    }

    public GroupByResult setDistributions(final List<DistributedByResult> distributions) {
      this.distributions = distributions;
      return this;
    }

    protected boolean canEqual(final Object other) {
      return other instanceof GroupByResult;
    }

    @Override
    public int hashCode() {
      final int PRIME = 59;
      int result = 1;
      final Object $key = getKey();
      result = result * PRIME + ($key == null ? 43 : $key.hashCode());
      final Object $label = getLabel();
      result = result * PRIME + ($label == null ? 43 : $label.hashCode());
      final Object $distributions = getDistributions();
      result = result * PRIME + ($distributions == null ? 43 : $distributions.hashCode());
      return result;
    }

    @Override
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
      final Object this$key = getKey();
      final Object other$key = other.getKey();
      if (this$key == null ? other$key != null : !this$key.equals(other$key)) {
        return false;
      }
      final Object this$label = getLabel();
      final Object other$label = other.getLabel();
      if (this$label == null ? other$label != null : !this$label.equals(other$label)) {
        return false;
      }
      final Object this$distributions = getDistributions();
      final Object other$distributions = other.getDistributions();
      if (this$distributions == null
          ? other$distributions != null
          : !this$distributions.equals(other$distributions)) {
        return false;
      }
      return true;
    }

    @Override
    public String toString() {
      return "CompositeCommandResult.GroupByResult(key="
          + getKey()
          + ", label="
          + getLabel()
          + ", distributions="
          + getDistributions()
          + ")";
    }
  }

  public static class DistributedByResult {

    private String key;
    private String label;
    private ViewResult viewResult;

    protected DistributedByResult(
        final String key, final String label, final ViewResult viewResult) {
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

    public DistributedByResult setLabel(final String label) {
      this.label = label;
      return this;
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
      return key;
    }

    public DistributedByResult setKey(final String key) {
      this.key = key;
      return this;
    }

    public ViewResult getViewResult() {
      return viewResult;
    }

    public DistributedByResult setViewResult(final ViewResult viewResult) {
      this.viewResult = viewResult;
      return this;
    }

    protected boolean canEqual(final Object other) {
      return other instanceof DistributedByResult;
    }

    @Override
    public int hashCode() {
      final int PRIME = 59;
      int result = 1;
      final Object $key = getKey();
      result = result * PRIME + ($key == null ? 43 : $key.hashCode());
      final Object $label = getLabel();
      result = result * PRIME + ($label == null ? 43 : $label.hashCode());
      final Object $viewResult = getViewResult();
      result = result * PRIME + ($viewResult == null ? 43 : $viewResult.hashCode());
      return result;
    }

    @Override
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
      final Object this$key = getKey();
      final Object other$key = other.getKey();
      if (this$key == null ? other$key != null : !this$key.equals(other$key)) {
        return false;
      }
      final Object this$label = getLabel();
      final Object other$label = other.getLabel();
      if (this$label == null ? other$label != null : !this$label.equals(other$label)) {
        return false;
      }
      final Object this$viewResult = getViewResult();
      final Object other$viewResult = other.getViewResult();
      if (this$viewResult == null
          ? other$viewResult != null
          : !this$viewResult.equals(other$viewResult)) {
        return false;
      }
      return true;
    }

    @Override
    public String toString() {
      return "CompositeCommandResult.DistributedByResult(key="
          + getKey()
          + ", label="
          + getLabel()
          + ", viewResult="
          + getViewResult()
          + ")";
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
      return viewMeasures;
    }

    public ViewResult setViewMeasures(final List<ViewMeasure> viewMeasures) {
      this.viewMeasures = viewMeasures;
      return this;
    }

    public List<? extends RawDataInstanceDto> getRawData() {
      return rawData;
    }

    public ViewResult setRawData(final List<? extends RawDataInstanceDto> rawData) {
      this.rawData = rawData;
      return this;
    }

    protected boolean canEqual(final Object other) {
      return other instanceof ViewResult;
    }

    @Override
    public int hashCode() {
      final int PRIME = 59;
      int result = 1;
      final Object $viewMeasures = getViewMeasures();
      result = result * PRIME + ($viewMeasures == null ? 43 : $viewMeasures.hashCode());
      final Object $rawData = getRawData();
      result = result * PRIME + ($rawData == null ? 43 : $rawData.hashCode());
      return result;
    }

    @Override
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
      final Object this$viewMeasures = getViewMeasures();
      final Object other$viewMeasures = other.getViewMeasures();
      if (this$viewMeasures == null
          ? other$viewMeasures != null
          : !this$viewMeasures.equals(other$viewMeasures)) {
        return false;
      }
      final Object this$rawData = getRawData();
      final Object other$rawData = other.getRawData();
      if (this$rawData == null ? other$rawData != null : !this$rawData.equals(other$rawData)) {
        return false;
      }
      return true;
    }

    @Override
    public String toString() {
      return "CompositeCommandResult.ViewResult(viewMeasures="
          + getViewMeasures()
          + ", rawData="
          + getRawData()
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
      return aggregationType;
    }

    public ViewMeasure setAggregationType(final AggregationDto aggregationType) {
      this.aggregationType = aggregationType;
      return this;
    }

    public UserTaskDurationTime getUserTaskDurationTime() {
      return userTaskDurationTime;
    }

    public ViewMeasure setUserTaskDurationTime(final UserTaskDurationTime userTaskDurationTime) {
      this.userTaskDurationTime = userTaskDurationTime;
      return this;
    }

    public Double getValue() {
      return value;
    }

    public ViewMeasure setValue(final Double value) {
      this.value = value;
      return this;
    }

    protected boolean canEqual(final Object other) {
      return other instanceof ViewMeasure;
    }

    @Override
    public int hashCode() {
      final int PRIME = 59;
      int result = 1;
      final Object $aggregationType = getAggregationType();
      result = result * PRIME + ($aggregationType == null ? 43 : $aggregationType.hashCode());
      final Object $userTaskDurationTime = getUserTaskDurationTime();
      result =
          result * PRIME + ($userTaskDurationTime == null ? 43 : $userTaskDurationTime.hashCode());
      final Object $value = getValue();
      result = result * PRIME + ($value == null ? 43 : $value.hashCode());
      return result;
    }

    @Override
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
      final Object this$aggregationType = getAggregationType();
      final Object other$aggregationType = other.getAggregationType();
      if (this$aggregationType == null
          ? other$aggregationType != null
          : !this$aggregationType.equals(other$aggregationType)) {
        return false;
      }
      final Object this$userTaskDurationTime = getUserTaskDurationTime();
      final Object other$userTaskDurationTime = other.getUserTaskDurationTime();
      if (this$userTaskDurationTime == null
          ? other$userTaskDurationTime != null
          : !this$userTaskDurationTime.equals(other$userTaskDurationTime)) {
        return false;
      }
      final Object this$value = getValue();
      final Object other$value = other.getValue();
      if (this$value == null ? other$value != null : !this$value.equals(other$value)) {
        return false;
      }
      return true;
    }

    @Override
    public String toString() {
      return "CompositeCommandResult.ViewMeasure(aggregationType="
          + getAggregationType()
          + ", userTaskDurationTime="
          + getUserTaskDurationTime()
          + ", value="
          + getValue()
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
        final AggregationDto aggregationType, final UserTaskDurationTime userTaskDurationTime) {
      this.aggregationType = aggregationType;
      this.userTaskDurationTime = userTaskDurationTime;
    }

    public ViewMeasureIdentifier() {}

    public AggregationDto getAggregationType() {
      return aggregationType;
    }

    public ViewMeasureIdentifier setAggregationType(final AggregationDto aggregationType) {
      this.aggregationType = aggregationType;
      return this;
    }

    public UserTaskDurationTime getUserTaskDurationTime() {
      return userTaskDurationTime;
    }

    public ViewMeasureIdentifier setUserTaskDurationTime(
        final UserTaskDurationTime userTaskDurationTime) {
      this.userTaskDurationTime = userTaskDurationTime;
      return this;
    }

    protected boolean canEqual(final Object other) {
      return other instanceof ViewMeasureIdentifier;
    }

    @Override
    public int hashCode() {
      final int PRIME = 59;
      int result = 1;
      final Object $aggregationType = getAggregationType();
      result = result * PRIME + ($aggregationType == null ? 43 : $aggregationType.hashCode());
      final Object $userTaskDurationTime = getUserTaskDurationTime();
      result =
          result * PRIME + ($userTaskDurationTime == null ? 43 : $userTaskDurationTime.hashCode());
      return result;
    }

    @Override
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
      final Object this$aggregationType = getAggregationType();
      final Object other$aggregationType = other.getAggregationType();
      if (this$aggregationType == null
          ? other$aggregationType != null
          : !this$aggregationType.equals(other$aggregationType)) {
        return false;
      }
      final Object this$userTaskDurationTime = getUserTaskDurationTime();
      final Object other$userTaskDurationTime = other.getUserTaskDurationTime();
      if (this$userTaskDurationTime == null
          ? other$userTaskDurationTime != null
          : !this$userTaskDurationTime.equals(other$userTaskDurationTime)) {
        return false;
      }
      return true;
    }

    @Override
    public String toString() {
      return "CompositeCommandResult.ViewMeasureIdentifier(aggregationType="
          + getAggregationType()
          + ", userTaskDurationTime="
          + getUserTaskDurationTime()
          + ")";
    }
  }
}
