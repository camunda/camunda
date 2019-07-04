/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.util;

import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportHyperMapResult;
import org.camunda.optimize.dto.optimize.query.report.single.result.HyperMapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.MapResultEntryDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;

public class HyperMapAsserter {

  private ProcessReportHyperMapResult expectedResult = new ProcessReportHyperMapResult();


    public static HyperMapAsserter asserter() {
      return new HyperMapAsserter();
    }

    public HyperMapAsserter processInstanceCount(long count) {
      expectedResult.setProcessInstanceCount(count);
      return this;
    }

    public HyperMapAsserter isComplete(boolean isComplete) {
      expectedResult.setIsComplete(isComplete);
      return this;
    }

    public GroupByAdder groupByContains(String groupByKey) {
      return new GroupByAdder(this, groupByKey);
    }

    public void doAssert(ProcessReportHyperMapResult actualResult) {
      // this is done by hand since it's otherwise really hard to see where the
      // assert failed.
      assertThat(actualResult.getProcessInstanceCount(), is(expectedResult.getProcessInstanceCount()));
      assertThat(actualResult.getIsComplete(), is(expectedResult.getIsComplete()));
      assertThat(actualResult.getData(), is(notNullValue()));
      assertThat(
        "The number of group by keys does not match!",
        actualResult.getData().size(),
        is(expectedResult.getData().size())
      );
      actualResult.getData().forEach(actualGroupByEntry -> {
        Optional<HyperMapResultEntryDto<Long>> expectedGroupBy =
          expectedResult.getDataEntryForKey(actualGroupByEntry.getKey());
        assertThat(
          String.format("Group by key [%s] should be present!", actualGroupByEntry.getKey()),
          expectedGroupBy.isPresent(),
          is(true)
        );
        assertThat(actualGroupByEntry.getValue().size(), is(expectedGroupBy.get().getValue().size()));
        assertThat(actualGroupByEntry.getLabel(), is(expectedGroupBy.get().getLabel()));
        actualGroupByEntry.getValue().forEach(actualDistributedBy -> {
          Optional<MapResultEntryDto<Long>> expectedDistributedBy = expectedGroupBy.get()
            .getDataEntryForKey(actualDistributedBy.getKey());
          assertThat(
            String.format("Distributed by key [%s] should be present!", actualDistributedBy.getKey()),
            expectedDistributedBy.isPresent(),
            is(true)
          );
          assertThat(actualDistributedBy.getValue(), is(expectedDistributedBy.get().getValue()));
          assertThat(actualDistributedBy.getLabel(), is(expectedDistributedBy.get().getLabel()));
        });
      });
      // this line is just to make sure that no new fields have been added that
      // should be compared and that the ordering of the lists matches.
      assertThat(actualResult, is(expectedResult));
    }

    private void addEntryToHyperMap(HyperMapResultEntryDto<Long> entry) {
      expectedResult.getData().add(entry);
    }

    public class GroupByAdder {

      private HyperMapAsserter asserter;
      private String groupByKey;
      private List<MapResultEntryDto<Long>> distributedByEntry = new ArrayList<>();

      public GroupByAdder(HyperMapAsserter asserter, String groupByKey) {
        this.asserter = asserter;
        this.groupByKey = groupByKey;
      }

      public GroupByAdder distributedByContains(String distributedByKey, Long result) {
        distributedByEntry.add(new MapResultEntryDto<>(distributedByKey, result, distributedByKey));
        return this;
      }

      public GroupByAdder distributedByContains(String distributedByKey, Long result, String label) {
        distributedByEntry.add(new MapResultEntryDto<>(distributedByKey, result, label));
        return this;
      }

      public GroupByAdder groupByContains(String groupByKey) {
        asserter.addEntryToHyperMap(new HyperMapResultEntryDto<>(this.groupByKey, distributedByEntry, this.groupByKey));
        return new GroupByAdder(asserter, groupByKey);
      }

      public void doAssert(ProcessReportHyperMapResult actualResult) {
        asserter.addEntryToHyperMap(new HyperMapResultEntryDto<>(this.groupByKey, distributedByEntry, this.groupByKey));
        asserter.doAssert(actualResult);
      }
    }
}
