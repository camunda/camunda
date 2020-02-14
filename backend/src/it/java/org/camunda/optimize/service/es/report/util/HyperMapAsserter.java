/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.util;

import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.HyperMapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.ReportHyperMapResultDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;

public class HyperMapAsserter {

  private ReportHyperMapResultDto expectedResult = new ReportHyperMapResultDto();


  public static HyperMapAsserter asserter() {
    return new HyperMapAsserter();
  }

  public HyperMapAsserter processInstanceCount(long count) {
    expectedResult.setInstanceCount(count);
    return this;
  }

  public HyperMapAsserter isComplete(boolean isComplete) {
    expectedResult.setIsComplete(isComplete);
    return this;
  }

  public GroupByAdder groupByContains(String groupByKey) {
    return new GroupByAdder(this, groupByKey);
  }

  public void doAssert(ReportHyperMapResultDto actualResult) {
    // this is done by hand since it's otherwise really hard to see where the
    // assert failed.
    assertThat(
      String.format(
        "Instance count should be [%s] but is [%s].",
        expectedResult.getInstanceCount(),
        actualResult.getInstanceCount()
      ),
      actualResult.getInstanceCount(),
      is(expectedResult.getInstanceCount())
    );
    assertThat(
      String.format(
        "IsComplete status should be [%s] but is [%s].",
        expectedResult.getIsComplete(),
        actualResult.getIsComplete()
      ),
      actualResult.getIsComplete(),
      is(expectedResult.getIsComplete())
    );
    assertThat("Data should not be null.", actualResult.getData(), is(notNullValue()));
    assertThat(
      "The number of group by keys does not match!",
      actualResult.getData().size(),
      is(expectedResult.getData().size())
    );

    actualResult.getData().forEach(actualGroupByEntry -> {
      Optional<HyperMapResultEntryDto> expectedGroupBy =
        expectedResult.getDataEntryForKey(actualGroupByEntry.getKey());
      doAssertsOnGroupByEntries(actualGroupByEntry, expectedGroupBy);

      actualGroupByEntry.getValue().forEach(actualDistributedBy -> {
        doAssertsOnGroupByEntryValue(actualDistributedBy, expectedGroupBy, actualGroupByEntry);
      });
    });

    // this line is just to make sure that no new fields have been added that
    // should be compared and that the ordering of the lists matches.
    assertThat(actualResult, is(expectedResult));
  }

  private void doAssertsOnGroupByEntries(final HyperMapResultEntryDto actualGroupByEntry,
                                         final Optional<HyperMapResultEntryDto> expectedGroupByEntry) {
    assertThat(
      String.format("Group by key [%s] should be present!", actualGroupByEntry.getKey()),
      expectedGroupByEntry.isPresent(),
      is(true)
    );
    assertThat(
      String.format(
        "Size of value of GroupByEntry with key [%s] should be [%s] but is [%s].",
        actualGroupByEntry.getKey(),
        expectedGroupByEntry.get().getValue().size(),
        actualGroupByEntry.getValue().size()
      ),
      actualGroupByEntry.getValue().size(),
      is(expectedGroupByEntry.get().getValue().size())
    );
    assertThat(
      String.format(
        "Label of GroupByEntry with key [%s] should be [%s] but is [%s].",
        actualGroupByEntry.getKey(),
        expectedGroupByEntry.get().getLabel(),
        actualGroupByEntry.getLabel()
      ),
      actualGroupByEntry.getLabel(),
      is(expectedGroupByEntry.get().getLabel())
    );
  }

  private void doAssertsOnGroupByEntryValue(final MapResultEntryDto actualDistributedBy,
                                            final Optional<HyperMapResultEntryDto> expectedGroupByEntry,
                                            final HyperMapResultEntryDto actualGroupByEntry) {
    Optional<MapResultEntryDto> expectedDistributedBy = expectedGroupByEntry.get()
      .getDataEntryForKey(actualDistributedBy.getKey());
    assertThat(
      String.format(
        "DistributedBy key [%s] should be present under GroupByEntry with key [%s].",
        actualDistributedBy.getKey(),
        expectedGroupByEntry.get().getKey()
      ),
      expectedDistributedBy.isPresent(),
      is(true)
    );
    assertThat(
      String.format(
        "Value for key [%s] - [%s] should be [%s], but is [%s]",
        actualGroupByEntry.getKey(),
        expectedDistributedBy.get().getKey(),
        expectedDistributedBy.get().getValue(),
        actualDistributedBy.getValue()
      ),
      actualDistributedBy.getValue(),
      is(expectedDistributedBy.get().getValue())
    );
    assertThat(
      String.format(
        "Label of DistributedByEntry with key [%s] should be [%s] but is [%s].",
        actualDistributedBy.getKey(),
        expectedDistributedBy.get().getLabel(),
        actualDistributedBy.getLabel()
      ),
      actualDistributedBy.getLabel(), is(expectedDistributedBy.get().getLabel())
    );
  }

  private void addEntryToHyperMap(HyperMapResultEntryDto entry) {
    expectedResult.getData().add(entry);
  }

  public class GroupByAdder {

    private HyperMapAsserter asserter;
    private String groupByKey;
    private List<MapResultEntryDto> distributedByEntry = new ArrayList<>();

    public GroupByAdder(HyperMapAsserter asserter, String groupByKey) {
      this.asserter = asserter;
      this.groupByKey = groupByKey;
    }

    public GroupByAdder distributedByContains(String distributedByKey, Long result) {
      distributedByEntry.add(new MapResultEntryDto(distributedByKey, result, distributedByKey));
      return this;
    }

    public GroupByAdder distributedByContains(String distributedByKey, Long result, String label) {
      distributedByEntry.add(new MapResultEntryDto(distributedByKey, result, label));
      return this;
    }

    public GroupByAdder groupByContains(String groupByKey) {
      asserter.addEntryToHyperMap(new HyperMapResultEntryDto(this.groupByKey, distributedByEntry, this.groupByKey));
      return new GroupByAdder(asserter, groupByKey);
    }

    public void doAssert(ReportHyperMapResultDto actualResult) {
      asserter.addEntryToHyperMap(new HyperMapResultEntryDto(this.groupByKey, distributedByEntry, this.groupByKey));
      asserter.doAssert(actualResult);
    }
  }
}
