/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.dashboard;

import static io.camunda.optimize.BusinessValueInstanceFixtures.bvdInstanceWithDuration;
import static io.camunda.optimize.service.dashboard.AgenticReportFilters.noExtraFilters;
import static io.camunda.optimize.service.dashboard.BusinessValueDashboardService.COUNT_BY_DATE_REPORT_ID;
import static io.camunda.optimize.service.dashboard.BusinessValueDashboardService.COUNT_BY_PROCESS_REPORT_ID;
import static io.camunda.optimize.service.dashboard.BusinessValueDashboardService.WORK_HANDLED_TOTAL_REPORT_ID;
import static io.camunda.optimize.service.util.importing.ZeebeConstants.ZEEBE_DEFAULT_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import io.camunda.optimize.AbstractBrokerlessZeebeCCSMIT;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.ProcessInstanceConstants;
import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.datasource.ZeebeDataSourceDto;
import io.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import io.camunda.optimize.service.report.ReportEvaluationService;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Evaluates the L0-activity / L1-overview volume tiles against a synthetic tenant so a regression
 * in the {@code PROCESS_INSTANCE FREQUENCY} view or the {@code completedInstancesOnly} filter fails
 * a targeted check rather than surfacing as a dashboard rendering bug. Runs against whichever
 * backend the IT suite is configured with.
 */
class BusinessValueVolumeTilesIT extends AbstractBrokerlessZeebeCCSMIT {

  private AgenticReportEvaluator reports;

  @BeforeEach
  void setUp() {
    embeddedOptimizeExtension.getBean(BusinessValueDashboardService.class).reconcile();
    reports =
        new AgenticReportEvaluator(
            embeddedOptimizeExtension.getBean(ReportEvaluationService.class));
  }

  @Test
  void shouldCountOnlyCompletedInstancesForWorkHandledTotal() {
    // given three completed instances and one running instance for the same process — the running
    // instance must be filtered out because the tile carries completedInstancesOnly()
    final String procKey = "vol-work-handled";
    final ProcessInstanceDto running =
        bvdInstanceWithDuration(procKey, 3_600_000L)
            .state(ProcessInstanceConstants.ACTIVE_STATE)
            .endDate(null)
            .duration(null)
            .build();
    persistProcessInstances(
        List.of(
            bvdInstanceWithDuration(procKey, 3_600_000L).build(),
            bvdInstanceWithDuration(procKey, 3_600_000L).build(),
            bvdInstanceWithDuration(procKey, 3_600_000L).build(),
            running));

    // when evaluating the aggregate work-handled tile
    // then only the three completed instances are counted
    assertThat(reports.evaluateNumber(WORK_HANDLED_TOTAL_REPORT_ID, noExtraFilters()))
        .isEqualTo(3.0);
  }

  @Test
  void shouldReturnPerProcessCountsSortedDescForCountByProcess() {
    // given four processes with distinct completed-instance counts
    final String hot = "vol-proc-hot";
    final String warm = "vol-proc-warm";
    final String cool = "vol-proc-cool";
    final String cold = "vol-proc-cold";
    final List<ProcessInstanceDto> instances = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      instances.add(bvdInstanceWithDuration(hot, 100L).build());
    }
    for (int i = 0; i < 3; i++) {
      instances.add(bvdInstanceWithDuration(warm, 100L).build());
    }
    for (int i = 0; i < 2; i++) {
      instances.add(bvdInstanceWithDuration(cool, 100L).build());
    }
    instances.add(bvdInstanceWithDuration(cold, 100L).build());
    persistProcessInstances(instances);

    // when evaluating the per-process count tile
    final List<MapResultEntryDto> result =
        reports.evaluateMapData(COUNT_BY_PROCESS_REPORT_ID, noExtraFilters());

    // then all four processes appear with the expected counts, sorted DESC by value — Hub reads
    // the top-N config off the tile and slices client-side, so the backend must return the full
    // ordered result
    assertThat(result)
        .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
        .containsExactly(tuple(hot, 5.0), tuple(warm, 3.0), tuple(cool, 2.0), tuple(cold, 1.0));
  }

  @Test
  void shouldBucketCompletedInstancesByEndDateForCountByDateTile() {
    // given three instances that ended on three different days
    final String procKey = "vol-count-date";
    final OffsetDateTime now = OffsetDateTime.now();
    persistProcessInstances(
        List.of(
            bvdInstanceWithDuration(procKey, 100L)
                .startDate(now.minusDays(1).minusHours(1))
                .endDate(now.minusDays(1))
                .build(),
            bvdInstanceWithDuration(procKey, 100L)
                .startDate(now.minusDays(2).minusHours(1))
                .endDate(now.minusDays(2))
                .build(),
            bvdInstanceWithDuration(procKey, 100L)
                .startDate(now.minusDays(3).minusHours(1))
                .endDate(now.minusDays(3))
                .build()));

    // when evaluating the momentum tile with AUTOMATIC bucketing
    final List<MapResultEntryDto> result =
        reports.evaluateMapData(COUNT_BY_DATE_REPORT_ID, noExtraFilters());

    // then the non-null buckets sum to the three completed instances — asserting on the total
    // rather than the bucket layout so the test is stable across AUTOMATIC unit choices
    final double bucketed =
        result.stream()
            .map(MapResultEntryDto::getValue)
            .filter(java.util.Objects::nonNull)
            .mapToDouble(Double::doubleValue)
            .sum();
    assertThat(bucketed).isEqualTo(3.0);
  }

  @Test
  void shouldLabelPerProcessVolumeBarsWithTheProcessName() {
    // given completed instances of a process whose BPMN id is not human-readable, plus a later
    // definition version carrying the name (the auto-seeded v1 defaults its name to the key)
    final String processKey = "order-fulfilment-v2";
    persistProcessInstances(List.of(bvdInstanceWithDuration(processKey, 100L).build()));
    persistProcessDefinitions(
        List.of(
            ProcessDefinitionOptimizeDto.builder()
                .id(processKey + ":2:2")
                .key(processKey)
                .version("2")
                .name("Order fulfilment")
                .dataSource(new ZeebeDataSourceDto("test-source", 1))
                .tenantId(ZEEBE_DEFAULT_TENANT_ID)
                .bpmn20Xml("<definitions/>")
                .build()));

    // when evaluating the per-process count tile
    final List<MapResultEntryDto> result =
        reports.evaluateMapData(COUNT_BY_PROCESS_REPORT_ID, noExtraFilters());

    // then the bar is still keyed by the BPMN process id but labelled with the process name
    assertThat(result)
        .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getLabel)
        .containsExactly(tuple(processKey, "Order fulfilment"));
  }

  @Test
  void shouldFallBackToTheProcessIdWhenNoDefinitionNameIsAvailable() {
    // given a process with no named definition version beyond the auto-seeded one
    final String processKey = "vol-unnamed-process";
    persistProcessInstances(List.of(bvdInstanceWithDuration(processKey, 100L).build()));

    // when evaluating the per-process count tile
    final List<MapResultEntryDto> result =
        reports.evaluateMapData(COUNT_BY_PROCESS_REPORT_ID, noExtraFilters());

    // then the label is never blank — it falls back to the id
    assertThat(result)
        .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getLabel)
        .containsExactly(tuple(processKey, processKey));
  }
}
