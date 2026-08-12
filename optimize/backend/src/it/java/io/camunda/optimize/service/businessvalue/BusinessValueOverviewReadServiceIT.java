/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.businessvalue;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.AbstractBrokerlessZeebeCCSMIT;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewDto;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewDto.AutomationRateBlock;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewDto.CycleTimeBlock;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewDto.MetricRange;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewResponseDto;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewResponseDto.OffTargetEntryDto;
import io.camunda.optimize.service.db.repository.BusinessValueOverviewRepository;
import io.camunda.optimize.service.util.importing.ZeebeConstants;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * End-to-end verification that {@code /business-value/overview} reads precomputed rows from the
 * live overview index and assembles them per tech design §5.3. The read service is bean-invoked
 * rather than reached over HTTP so the test does not have to authenticate; controller behavior is a
 * straight delegation and exercised by the unit test.
 */
class BusinessValueOverviewReadServiceIT extends AbstractBrokerlessZeebeCCSMIT {

  private static final String DEFAULT_TENANT = ZeebeConstants.ZEEBE_DEFAULT_TENANT_ID;
  private static final String USER = "demo";

  private BusinessValueOverviewRepository overviewRepository;
  private BusinessValueOverviewReadService readService;

  @BeforeEach
  void setUp() {
    overviewRepository = embeddedOptimizeExtension.getBean(BusinessValueOverviewRepository.class);
    readService = embeddedOptimizeExtension.getBean(BusinessValueOverviewReadService.class);
  }

  @Test
  void shouldReturnEmptyResponseWhenNoRowsIndexed() {
    // when
    final BusinessValueOverviewResponseDto response =
        readService.getOverview(USER, MetricRange.THIRTY_DAYS);

    // then
    assertThat(response.hasAnyTarget()).isFalse();
    assertThat(response.coverage().totalProcesses()).isZero();
    assertThat(response.attainment().targetsSet()).isZero();
    assertThat(response.categories()).hasSize(2);
    assertThat(response.offTarget()).isEmpty();
  }

  @Test
  void shouldAssembleResponseFromIndexRows() {
    // given — three rows in the requested range: one met, one missed, one target-less
    overviewRepository.bulkUpsert(
        List.of(
            fresh(
                "invoice-met",
                new CycleTimeBlock(6_000L, 8_000L, true),
                new AutomationRateBlock(null, null, null),
                1,
                1),
            fresh(
                "invoice-missed",
                new CycleTimeBlock(10_000L, 8_000L, false),
                new AutomationRateBlock(70.0, 85, false),
                2,
                0),
            fresh(
                "onboarding-empty",
                new CycleTimeBlock(null, null, null),
                new AutomationRateBlock(null, null, null),
                0,
                0)),
        true);

    // when
    final BusinessValueOverviewResponseDto response =
        readService.getOverview(USER, MetricRange.THIRTY_DAYS);

    // then
    assertThat(response.hasAnyTarget()).isTrue();
    assertThat(response.coverage().processesWithTarget()).isEqualTo(2);
    assertThat(response.coverage().totalProcesses()).isEqualTo(3);
    assertThat(response.attainment().targetsSet()).isEqualTo(3);
    assertThat(response.attainment().targetsMet()).isEqualTo(1);

    // off-target sorted by gapPct desc: cycleTime 25%, automation 17.6%
    assertThat(response.offTarget())
        .extracting(OffTargetEntryDto::kpi, OffTargetEntryDto::processKey)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("cycleTime", "invoice-missed"),
            org.assertj.core.groups.Tuple.tuple("automationRate", "invoice-missed"));
    assertThat(response.offTarget().get(0).gapPct()).isEqualTo(25.0);
    assertThat(response.offTarget().get(0).displayUnit()).isEqualTo("HOURS");
  }

  @Test
  void shouldOnlyIncludeRowsForTheRequestedRange() {
    // given — rows for two ranges
    overviewRepository.bulkUpsert(
        List.of(
            fresh(
                MetricRange.THIRTY_DAYS,
                "proc-30d",
                new CycleTimeBlock(6_000L, 8_000L, true),
                new AutomationRateBlock(null, null, null),
                1,
                1),
            fresh(
                MetricRange.SEVEN_DAYS,
                "proc-7d",
                new CycleTimeBlock(6_000L, 8_000L, true),
                new AutomationRateBlock(null, null, null),
                1,
                1)),
        true);

    // when
    final BusinessValueOverviewResponseDto response =
        readService.getOverview(USER, MetricRange.THIRTY_DAYS);

    // then — only the 30d row is counted
    assertThat(response.coverage().totalProcesses()).isEqualTo(1);
  }

  @Test
  void shouldFireStaleReadBackstopAndRefreshLastComputedAt() {
    // given — a stale row from three refresh intervals ago (default = 86400s)
    final String processKey = "invoice-stale-" + System.nanoTime();
    final OffsetDateTime staleTimestamp = OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(300_000L);
    overviewRepository.bulkUpsert(
        List.of(
            rowAt(
                MetricRange.THIRTY_DAYS,
                processKey,
                new CycleTimeBlock(null, null, null),
                new AutomationRateBlock(null, null, null),
                0,
                0,
                staleTimestamp)),
        true);

    // when
    readService.getOverview(USER, MetricRange.THIRTY_DAYS);

    // then — the async backstop must refresh the row's lastComputedAt
    Awaitility.await()
        .atMost(java.time.Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              final OffsetDateTime latest =
                  overviewRepository
                      .getByKey(DEFAULT_TENANT, processKey, MetricRange.THIRTY_DAYS)
                      .orElseThrow()
                      .getLastComputedAt();
              assertThat(latest).isAfter(staleTimestamp);
            });
  }

  private static BusinessValueOverviewDto fresh(
      final String processKey,
      final CycleTimeBlock cycleTime,
      final AutomationRateBlock automationRate,
      final int targetsSet,
      final int targetsMet) {
    return fresh(
        MetricRange.THIRTY_DAYS, processKey, cycleTime, automationRate, targetsSet, targetsMet);
  }

  private static BusinessValueOverviewDto fresh(
      final MetricRange range,
      final String processKey,
      final CycleTimeBlock cycleTime,
      final AutomationRateBlock automationRate,
      final int targetsSet,
      final int targetsMet) {
    return rowAt(
        range,
        processKey,
        cycleTime,
        automationRate,
        targetsSet,
        targetsMet,
        OffsetDateTime.now(ZoneOffset.UTC));
  }

  private static BusinessValueOverviewDto rowAt(
      final MetricRange range,
      final String processKey,
      final CycleTimeBlock cycleTime,
      final AutomationRateBlock automationRate,
      final int targetsSet,
      final int targetsMet,
      final OffsetDateTime lastComputedAt) {
    return new BusinessValueOverviewDto(
        DEFAULT_TENANT,
        processKey,
        processKey,
        range,
        lastComputedAt,
        cycleTime,
        automationRate,
        targetsSet > 0,
        targetsSet,
        targetsMet);
  }
}
