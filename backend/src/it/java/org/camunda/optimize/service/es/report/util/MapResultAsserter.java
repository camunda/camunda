/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.util;

import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.result.MeasureDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class MapResultAsserter {

  private final ReportMapResultDto expectedResult = new ReportMapResultDto();

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

  @Deprecated
  public MapResultAsserter groupedByContains(String distributedByKey, Double result) {
    return this;
  }

  @Deprecated
  public MapResultAsserter groupedByContains(String distributedByKey, Double result, String label) {
    return this;
  }

  public MeasureAdder measure(final ViewProperty viewProperty) {
    return measure(viewProperty, null);
  }

  public MeasureAdder measure(final ViewProperty viewProperty,
                                               final AggregationType aggregationType) {
    return measure(viewProperty, aggregationType, null);
  }

  public MeasureAdder measure(final ViewProperty viewProperty,
                                               final AggregationType aggregationType,
                                               final UserTaskDurationTime userTaskDurationTime) {
    return new MeasureAdder(this, viewProperty, aggregationType, userTaskDurationTime);
  }

  private void addMeasure(MeasureDto<List<MapResultEntryDto>> measure) {
    expectedResult.addMeasure(measure);
  }

  public void doAssert(ReportMapResultDto actualResult) {
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
    assertThat(actualResult.getFirstMeasureData())
      .as("Data should not be null.")
      .isNotNull();
    assertThat(actualResult.getFirstMeasureData().size())
      .as("The number of group by keys does not match!")
      .isEqualTo(expectedResult.getFirstMeasureData().size());

    actualResult.getFirstMeasureData().forEach(actualGroupByEntry -> {
      Optional<MapResultEntryDto> expectedGroupBy =
        expectedResult.getEntryForKey(actualGroupByEntry.getKey());
      doAssertsOnGroupByEntry(actualGroupByEntry, expectedGroupBy);
    });

    // this line is just to make sure that no new fields have been added that
    // should be compared and that the ordering of the lists matches.
    assertThat(actualResult).isEqualTo(expectedResult);
  }

  public class MeasureAdder {

    private MapResultAsserter asserter;
    private MeasureDto<List<MapResultEntryDto>> measure;

    public MeasureAdder(final MapResultAsserter mapAsserter,
                        final ViewProperty viewProperty,
                        final AggregationType aggregationType,
                        final UserTaskDurationTime userTaskDurationTime) {
      this.asserter = mapAsserter;
      this.measure = MeasureDto.of(viewProperty, aggregationType, userTaskDurationTime, new ArrayList<>());

    }

    public MeasureAdder groupedByContains(String distributedByKey, Double result) {
      measure.getData().add(new MapResultEntryDto(distributedByKey, result, distributedByKey));
      return this;
    }

    public MeasureAdder groupedByContains(String distributedByKey, Double result, String label) {
      measure.getData().add(new MapResultEntryDto(distributedByKey, result, label));
      return this;
    }

    public MapResultAsserter add() {
      asserter.addMeasure(measure);
      return asserter;
    }

    public void doAssert(ReportMapResultDto actualResult) {
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
