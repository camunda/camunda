/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.util;

import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.measure.MapMeasureResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.measure.MeasureResponseDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class MapResultAsserter {

  private final ReportResultResponseDto<List<MapResultEntryDto>> expectedResult = new ReportResultResponseDto<>();

  public static MapResultAsserter asserter() {
    return new MapResultAsserter();
  }

  public MapResultAsserter processInstanceCount(long count) {
    expectedResult.setInstanceCount(count);
    expectedResult.setInstanceCountWithoutFilters(count);
    return this;
  }

  public MapResultAsserter processInstanceCountWithoutFilters(long count) {
    expectedResult.setInstanceCountWithoutFilters(count);
    return this;
  }

  public MeasureAdder measure(final ViewProperty viewProperty) {
    return measure(viewProperty, null);
  }

  public MeasureAdder measure(final ViewProperty viewProperty,
                              final AggregationDto aggregationType) {
    return measure(viewProperty, aggregationType, null);
  }

  public MeasureAdder measure(final ViewProperty viewProperty,
                              final AggregationDto aggregationType,
                              final UserTaskDurationTime userTaskDurationTime) {
    return new MeasureAdder(this, viewProperty, aggregationType, userTaskDurationTime);
  }

  private void addMeasure(MeasureResponseDto<List<MapResultEntryDto>> measure) {
    expectedResult.addMeasure(measure);
  }

  public void doAssert(ReportResultResponseDto<List<MapResultEntryDto>> actualResult) {
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
    assertThat(actualResult.getFirstMeasureData().size())
      .as("The number of group by keys does not match!")
      .isEqualTo(expectedResult.getFirstMeasureData().size());

    actualResult.getFirstMeasureData().forEach(actualGroupByEntry -> {
      Optional<MapResultEntryDto> expectedGroupBy =
        MapResultUtil.getEntryForKey(expectedResult.getFirstMeasureData(), actualGroupByEntry.getKey());
      doAssertsOnGroupByEntry(actualGroupByEntry, expectedGroupBy);
    });

    // this line is just to make sure that no new fields have been added that
    // should be compared and that the ordering of the lists matches.
    assertThat(actualResult).isEqualTo(expectedResult);
  }

  public class MeasureAdder {

    private final MapResultAsserter asserter;
    private final MapMeasureResponseDto measure;

    public MeasureAdder(final MapResultAsserter mapAsserter,
                        final ViewProperty viewProperty,
                        final AggregationDto aggregationType,
                        final UserTaskDurationTime userTaskDurationTime) {
      this.asserter = mapAsserter;
      this.measure = MapMeasureResponseDto.builder()
        .property(viewProperty)
        .aggregationType(aggregationType)
        .userTaskDurationTime(userTaskDurationTime)
        .data(new ArrayList<>())
        .build();

    }

    public MeasureAdder groupedByContains(String groupByKey, Double result) {
      measure.getData().add(new MapResultEntryDto(groupByKey, result, groupByKey));
      return this;
    }

    public MeasureAdder groupedByContains(String groupByKey, Double result, String label) {
      measure.getData().add(new MapResultEntryDto(groupByKey, result, label));
      return this;
    }

    public MapResultAsserter add() {
      asserter.addMeasure(measure);
      return asserter;
    }

    public void doAssert(ReportResultResponseDto<List<MapResultEntryDto>> actualResult) {
      add();
      asserter.doAssert(actualResult);
    }
  }

  private void doAssertsOnGroupByEntry(final MapResultEntryDto actualGroupByEntry,
                                       final Optional<MapResultEntryDto> expectedGroupByEntry) {

    assertThat(expectedGroupByEntry)
      .as(String.format("Group by key [%s] should be present!", actualGroupByEntry.getKey()))
      .isPresent();
    assertThat(actualGroupByEntry.getValue())
      .as(String.format(
        "The value of GroupByEntry with key [%s] should be [%s] but is [%s].",
        actualGroupByEntry.getKey(),
        expectedGroupByEntry.get().getValue(),
        actualGroupByEntry.getValue()
      ))
      .isEqualTo(expectedGroupByEntry.get().getValue());
    assertThat(actualGroupByEntry.getLabel())
      .as(String.format(
        "Label of GroupByEntry with key [%s] should be [%s] but is [%s].",
        actualGroupByEntry.getKey(),
        expectedGroupByEntry.get().getLabel(),
        actualGroupByEntry.getLabel()
      ))
      .isEqualTo(expectedGroupByEntry.get().getLabel());
  }
}
