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
      case ReportSortingDto.SORT_BY_VALUE:
        valueToSortByExtractor = MapResultEntryDto::getValue;
        break;
      case ReportSortingDto.SORT_BY_LABEL:
        valueToSortByExtractor = entry -> entry.getLabel().toLowerCase(Locale.ENGLISH);
        break;
      case ReportSortingDto.SORT_BY_KEY:
      default:
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
                case ReportSortingDto.SORT_BY_LABEL:
                  valueToSortByExtractor = entry -> entry.getLabel().toLowerCase(Locale.ENGLISH);
                  break;
                case ReportSortingDto.SORT_BY_KEY:
                default:
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

  public void setGroupBySorting(final ReportSortingDto groupBySorting) {
    this.groupBySorting = groupBySorting;
  }

  public ReportSortingDto getDistributedBySorting() {
    return distributedBySorting;
  }

  public void setDistributedBySorting(final ReportSortingDto distributedBySorting) {
    this.distributedBySorting = distributedBySorting;
  }

  public boolean isGroupByKeyOfNumericType() {
    return isGroupByKeyOfNumericType;
  }

  public void setGroupByKeyOfNumericType(final boolean isGroupByKeyOfNumericType) {
    this.isGroupByKeyOfNumericType = isGroupByKeyOfNumericType;
  }

  public boolean isDistributedByKeyOfNumericType() {
    return isDistributedByKeyOfNumericType;
  }

  public void setDistributedByKeyOfNumericType(final boolean isDistributedByKeyOfNumericType) {
    this.isDistributedByKeyOfNumericType = isDistributedByKeyOfNumericType;
  }

  public Supplier<Double> getDefaultNumberValueSupplier() {
    return defaultNumberValueSupplier;
  }

  public void setDefaultNumberValueSupplier(final Supplier<Double> defaultNumberValueSupplier) {
    this.defaultNumberValueSupplier = defaultNumberValueSupplier;
  }

  public List<GroupByResult> getGroups() {
    return groups;
  }

  public void setGroups(final List<GroupByResult> groups) {
    this.groups = groups;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof CompositeCommandResult;
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

    public void setLabel(final String label) {
      this.label = label;
    }

    public String getKey() {
      return key;
    }

    public void setKey(final String key) {
      this.key = key;
    }

    public List<DistributedByResult> getDistributions() {
      return distributions;
    }

    public void setDistributions(final List<DistributedByResult> distributions) {
      this.distributions = distributions;
    }

    protected boolean canEqual(final Object other) {
      return other instanceof GroupByResult;
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

    public void setLabel(final String label) {
      this.label = label;
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

    public void setKey(final String key) {
      this.key = key;
    }

    public ViewResult getViewResult() {
      return viewResult;
    }

    public void setViewResult(final ViewResult viewResult) {
      this.viewResult = viewResult;
    }

    protected boolean canEqual(final Object other) {
      return other instanceof DistributedByResult;
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

    public void setViewMeasures(final List<ViewMeasure> viewMeasures) {
      this.viewMeasures = viewMeasures;
    }

    public List<? extends RawDataInstanceDto> getRawData() {
      return rawData;
    }

    public void setRawData(final List<? extends RawDataInstanceDto> rawData) {
      this.rawData = rawData;
    }

    protected boolean canEqual(final Object other) {
      return other instanceof ViewResult;
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

    public void setAggregationType(final AggregationDto aggregationType) {
      this.aggregationType = aggregationType;
    }

    public UserTaskDurationTime getUserTaskDurationTime() {
      return userTaskDurationTime;
    }

    public void setUserTaskDurationTime(final UserTaskDurationTime userTaskDurationTime) {
      this.userTaskDurationTime = userTaskDurationTime;
    }

    public Double getValue() {
      return value;
    }

    public void setValue(final Double value) {
      this.value = value;
    }

    protected boolean canEqual(final Object other) {
      return other instanceof ViewMeasure;
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

    public void setAggregationType(final AggregationDto aggregationType) {
      this.aggregationType = aggregationType;
    }

    public UserTaskDurationTime getUserTaskDurationTime() {
      return userTaskDurationTime;
    }

    public void setUserTaskDurationTime(final UserTaskDurationTime userTaskDurationTime) {
      this.userTaskDurationTime = userTaskDurationTime;
    }

    protected boolean canEqual(final Object other) {
      return other instanceof ViewMeasureIdentifier;
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
      return "CompositeCommandResult.ViewMeasureIdentifier(aggregationType="
          + getAggregationType()
          + ", userTaskDurationTime="
          + getUserTaskDurationTime()
          + ")";
    }
  }
}
