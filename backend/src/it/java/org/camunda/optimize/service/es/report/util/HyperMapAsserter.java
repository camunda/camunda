/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.util;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.HyperMapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.measure.HyperMapMeasureResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.measure.MeasureResponseDto;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@RequiredArgsConstructor
public class HyperMapAsserter {

  private final ReportResultResponseDto<List<HyperMapResultEntryDto>> expectedResult = new ReportResultResponseDto<>();

  public static HyperMapAsserter asserter() {
    return new HyperMapAsserter();
  }

  public HyperMapAsserter processInstanceCount(long count) {
    expectedResult.setInstanceCount(count);
    return this;
  }

  public HyperMapAsserter processInstanceCountWithoutFilters(long count) {
    expectedResult.setInstanceCountWithoutFilters(count);
    return this;
  }

  public MeasureAdder measure(final ViewProperty viewProperty) {
    return new MeasureAdder(this, viewProperty, null, null);
  }

  public MeasureAdder measure(final ViewProperty viewProperty,
                              final AggregationDto aggregationType) {
    return measure(viewProperty, aggregationType, null);
  }

  public MeasureAdder measure(final ViewProperty viewProperty,
                              final AggregationType aggregationType) {
    return measure(viewProperty, aggregationType, null);
  }

  public MeasureAdder measure(final ViewProperty viewProperty,
                              final AggregationType aggregationType,
                              final UserTaskDurationTime userTaskDurationTime) {
    return new MeasureAdder(this, viewProperty, new AggregationDto(aggregationType), userTaskDurationTime);
  }

  public MeasureAdder measure(final ViewProperty viewProperty,
                              final AggregationDto aggregationType,
                              final UserTaskDurationTime userTaskDurationTime) {
    return new MeasureAdder(this, viewProperty, aggregationType, userTaskDurationTime);
  }

  public void doAssert(ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult) {
    // this is done by hand since it's otherwise really hard to see where the
    // assert failed.
    assertThat(actualResult.getInstanceCount())
      .as(String.format(
        "Instance count should be [%s] but is [%s].",
        expectedResult.getInstanceCount(),
        actualResult.getInstanceCount()
      ))
      .isEqualTo(expectedResult.getInstanceCount());
    assertThat(actualResult.getInstanceCountWithoutFilters())
      .as(String.format(
        "Instance count without filters should be [%s] but is [%s].",
        expectedResult.getInstanceCountWithoutFilters(),
        actualResult.getInstanceCountWithoutFilters()
      ))
      .isEqualTo(expectedResult.getInstanceCountWithoutFilters());
    assertThat(actualResult.getMeasures().size())
      .as(String.format(
        "Number of Measure entries should be [%s] but is [%s].",
        expectedResult.getMeasures().size(),
        actualResult.getMeasures().size()
      ))
      .isEqualTo(expectedResult.getMeasures().size());
    assertThat(actualResult.getFirstMeasureData())
      .as("Data should not be null.")
      .isNotNull();
    assertThat(actualResult.getFirstMeasureData())
      .as("The number of group by keys does not match!")
      .hasSize(expectedResult.getFirstMeasureData().size());

    actualResult.getFirstMeasureData().forEach(actualGroupByEntry -> {
      Optional<HyperMapResultEntryDto> expectedGroupBy =
        MapResultUtil.getDataEntryForKey(expectedResult.getFirstMeasureData(), actualGroupByEntry.getKey());
      doAssertsOnGroupByEntries(actualGroupByEntry, expectedGroupBy);

      actualGroupByEntry.getValue().forEach(actualDistributedBy -> {
        doAssertsOnGroupByEntryValue(actualDistributedBy, expectedGroupBy, actualGroupByEntry);
      });
    });

    // this line is just to make sure that no new fields have been added that
    // should be compared and that the ordering of the lists matches.
    assertThat(actualResult).isEqualTo(expectedResult);
  }

  private void doAssertsOnGroupByEntries(final HyperMapResultEntryDto actualGroupByEntry,
                                         final Optional<HyperMapResultEntryDto> expectedGroupByEntry) {
    assertThat(expectedGroupByEntry)
      .as(String.format("Group by key [%s] should be present!", actualGroupByEntry.getKey()))
      .isPresent();
    assertThat(actualGroupByEntry.getValue())
      .as(String.format(
        "Size of value of GroupByEntry with key [%s] should be [%s] but is [%s].",
        actualGroupByEntry.getKey(),
        expectedGroupByEntry.get().getValue().size(),
        actualGroupByEntry.getValue().size()
      ))
      .hasSize(expectedGroupByEntry.get().getValue().size());
    assertThat(actualGroupByEntry.getLabel())
      .as(String.format(
        "Label of GroupByEntry with key [%s] should be [%s] but is [%s].",
        actualGroupByEntry.getKey(),
        expectedGroupByEntry.get().getLabel(),
        actualGroupByEntry.getLabel()
      ))
      .isEqualTo(expectedGroupByEntry.get().getLabel());
  }

  private void doAssertsOnGroupByEntryValue(final MapResultEntryDto actualDistributedBy,
                                            final Optional<HyperMapResultEntryDto> expectedGroupByEntry,
                                            final HyperMapResultEntryDto actualGroupByEntry) {
    assertThat(expectedGroupByEntry).isPresent();
    Optional<MapResultEntryDto> expectedDistributedBy = MapResultUtil.getDataEntryForKey(
      expectedGroupByEntry.get(), actualDistributedBy.getKey()
    );
    assertThat(expectedDistributedBy)
      .as(String.format(
        "DistributedBy key [%s] should be present under GroupByEntry with key [%s].",
        actualDistributedBy.getKey(),
        expectedGroupByEntry.get().getKey()
      ))
      .isPresent();
    assertThat(actualDistributedBy.getValue())
      .as(String.format(
        "Value for key [%s] - [%s] should be [%s], but is [%s]",
        actualGroupByEntry.getKey(),
        expectedDistributedBy.get().getKey(),
        expectedDistributedBy.get().getValue(),
        actualDistributedBy.getValue()
      ))
      .isEqualTo(expectedDistributedBy.get().getValue());
    assertThat(actualDistributedBy.getLabel())
      .as(String.format(
        "Label of DistributedByEntry with key [%s] should be [%s] but is [%s].",
        actualDistributedBy.getKey(),
        expectedDistributedBy.get().getLabel(),
        actualDistributedBy.getLabel()
      ))
      .isEqualTo(expectedDistributedBy.get().getLabel());
  }

  private void addMeasure(MeasureResponseDto<List<HyperMapResultEntryDto>> measure) {
    expectedResult.addMeasure(measure);
  }

  public class MeasureAdder {

    private HyperMapAsserter asserter;
    private MeasureResponseDto<List<HyperMapResultEntryDto>> measure;

    public MeasureAdder(final HyperMapAsserter hyperMapAsserter,
                        final ViewProperty viewProperty,
                        final AggregationDto aggregationType,
                        final UserTaskDurationTime userTaskDurationTime) {
      this.asserter = hyperMapAsserter;
      this.measure = HyperMapMeasureResponseDto.builder()
        .property(viewProperty)
        .aggregationType(aggregationType)
        .userTaskDurationTime(userTaskDurationTime)
        .data(new ArrayList<>())
        .build();

    }

    public GroupByAdder groupByContains(final String groupByKey) {
      return new GroupByAdder(this, groupByKey);
    }

    public GroupByAdder groupByContains(final String groupByKey, final String groupByLabel) {
      return new GroupByAdder(this, groupByKey, groupByLabel);
    }

    public HyperMapAsserter add() {
      asserter.addMeasure(measure);
      return asserter;
    }

    private void addEntryToHyperMap(final HyperMapResultEntryDto entry) {
      measure.getData().add(entry);
    }

    public void doAssert(ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult) {
      add();
      asserter.doAssert(actualResult);
    }
  }

  public class GroupByAdder {

    private MeasureAdder measureAdder;
    private String groupByKey;
    private String groupByLabel;
    private List<MapResultEntryDto> distributedByEntry = new ArrayList<>();

    public GroupByAdder(final MeasureAdder measureAdder, final String groupByKey, final String groupByLabel) {
      this.measureAdder = measureAdder;
      this.groupByKey = groupByKey;
      this.groupByLabel = groupByLabel;
    }

    public GroupByAdder(final MeasureAdder measureAdder, final String groupByKey) {
      this(measureAdder, groupByKey, groupByKey);
    }

    public GroupByAdder distributedByContains(Collection<MapResultEntryDto> entries) {
      distributedByEntry.addAll(entries);
      return this;
    }

    public GroupByAdder distributedByContains(String distributedByKey, Double result) {
      distributedByEntry.add(new MapResultEntryDto(distributedByKey, result, distributedByKey));
      return this;
    }

    public GroupByAdder distributedByContains(String distributedByKey, Double result, String label) {
      distributedByEntry.add(new MapResultEntryDto(distributedByKey, result, label));
      return this;
    }

    public GroupByAdder groupByContains(final String groupByKey) {
      return groupByContains(groupByKey, groupByKey);
    }

    public GroupByAdder groupByContains(final String groupByKey, final String groupByLabel) {
      add();
      return new GroupByAdder(measureAdder, groupByKey, groupByLabel);
    }

    public MeasureAdder add() {
      measureAdder.addEntryToHyperMap(new HyperMapResultEntryDto(
        this.groupByKey,
        distributedByEntry,
        this.groupByLabel
      ));
      return measureAdder;
    }

    public void doAssert(ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult) {
      add();
      measureAdder.doAssert(actualResult);
    }

  }
}
