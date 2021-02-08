/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.util;

import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;

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

  public MapResultAsserter groupedByContains(String distributedByKey, Double result) {
    expectedResult.getData().add(new MapResultEntryDto(distributedByKey, result, distributedByKey));
    return this;
  }

  public MapResultAsserter groupedByContains(String distributedByKey, Double result, String label) {
    expectedResult.getData().add(new MapResultEntryDto(distributedByKey, result, label));
    return this;
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
    assertThat(actualResult.getData())
      .as("Data should not be null.")
      .isNotNull();
    assertThat(actualResult.getData().size())
      .as("The number of group by keys does not match!")
      .isEqualTo(expectedResult.getData().size());

    actualResult.getData().forEach(actualGroupByEntry -> {
      Optional<MapResultEntryDto> expectedGroupBy =
        expectedResult.getEntryForKey(actualGroupByEntry.getKey());
      doAssertsOnGroupByEntry(actualGroupByEntry, expectedGroupBy);
    });

    // this line is just to make sure that no new fields have been added that
    // should be compared and that the ordering of the lists matches.
    assertThat(actualResult).isEqualTo(expectedResult);
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
