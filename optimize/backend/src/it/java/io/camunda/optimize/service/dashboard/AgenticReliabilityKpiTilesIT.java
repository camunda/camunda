/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.dashboard;

import static io.camunda.optimize.AgenticInstanceFixtures.PROC_KEY;
import static io.camunda.optimize.AgenticInstanceFixtures.agenticInstance;
import static io.camunda.optimize.AgenticInstanceFixtures.agenticInstanceWithTokens;
import static io.camunda.optimize.AgenticInstanceFixtures.repeat;
import static io.camunda.optimize.AgenticInstanceFixtures.resolvedIncident;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.FAILURE_RATE_BY_VERSION_REPORT_ID;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.KPI_INCIDENT_RATE_REPORT_ID;
import static io.camunda.optimize.service.dashboard.AgenticReportFilters.noExtraFilters;
import static io.camunda.optimize.service.dashboard.AgenticReportFilters.rollingEndDateFilter;
import static io.camunda.optimize.service.dashboard.AgenticReportFilters.withDefinitions;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.AbstractBrokerlessZeebeCCSMIT;
import io.camunda.optimize.dto.optimize.ProcessInstanceConstants;
import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateUnit;
import io.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import io.camunda.optimize.service.report.ReportEvaluationService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgenticReliabilityKpiTilesIT extends AbstractBrokerlessZeebeCCSMIT {

  private AgenticReportEvaluator reports;

  @BeforeEach
  void setUp() {
    embeddedOptimizeExtension.getBean(AgenticControlDashboardService.class).reconcile();
    reports =
        new AgenticReportEvaluator(
            embeddedOptimizeExtension.getBean(ReportEvaluationService.class));
  }

  private static Double rateForVersion(
      final List<MapResultEntryDto> buckets, final String processDefinitionVersion) {
    return buckets.stream()
        .filter(entry -> processDefinitionVersion.equals(entry.getKey()))
        .map(MapResultEntryDto::getValue)
        .findFirst()
        .orElse(null);
  }

  @Test
  void shouldCountResolvedIncidentsOnCompletedAgenticInstances() {
    final String instanceId2 = UUID.randomUUID().toString();

    // one resolved incident
    final ProcessInstanceDto withIncident =
        resolvedIncident(agenticInstanceWithTokens(PROC_KEY, 100L, 50L));

    // no incidents
    final ProcessInstanceDto clean =
        agenticInstanceWithTokens(PROC_KEY, 100L, 50L).processInstanceId(instanceId2).build();

    // running instance with a resolved incident — must not be counted
    final ProcessInstanceDto running =
        resolvedIncident(
            agenticInstanceWithTokens(PROC_KEY, 100L, 50L)
                .state(ProcessInstanceConstants.ACTIVE_STATE)
                .endDate(null)
                .duration(null));

    persistProcessInstances(List.of(withIncident, clean, running));

    final Double result = reports.evaluateNumber(KPI_INCIDENT_RATE_REPORT_ID, noExtraFilters());
    // 1 instance with resolved incident / 2 completed agentic instances = 50%
    assertThat(result).isEqualTo(50.0);
  }

  @Test
  void shouldApplyDateFilterToIncidentRate() {
    // within window: 1 resolved incident
    final ProcessInstanceDto recent =
        resolvedIncident(
            agenticInstanceWithTokens(PROC_KEY, 100L, 50L)
                .startDate(OffsetDateTime.now().minusHours(3))
                .endDate(OffsetDateTime.now().minusHours(2)));
    // outside window: 1 resolved incident — should be excluded
    final ProcessInstanceDto old =
        resolvedIncident(
            agenticInstanceWithTokens(PROC_KEY, 100L, 50L)
                .startDate(OffsetDateTime.now().minusDays(10))
                .endDate(OffsetDateTime.now().minusDays(9)));

    persistProcessInstances(List.of(recent, old));

    // 1 instance with resolved incident / 1 completed agentic instance in window = 100%
    assertThat(
            reports.evaluateNumber(
                KPI_INCIDENT_RATE_REPORT_ID, rollingEndDateFilter(1L, DateUnit.DAYS)))
        .isEqualTo(100.0);
  }

  @Test
  void shouldApplyDefinitionFilterToIncidentRate() {
    final String procKeyA = "proc-inc-a";
    final String procKeyB = "proc-inc-b";

    persistProcessInstances(
        List.of(
            resolvedIncident(agenticInstanceWithTokens(procKeyA, 100L, 50L)),
            resolvedIncident(agenticInstanceWithTokens(procKeyB, 100L, 50L))));

    // only procKeyA selected → 1 instance with incident / 1 completed agentic instance = 100%
    assertThat(
            reports.evaluateNumber(
                KPI_INCIDENT_RATE_REPORT_ID,
                withDefinitions(List.of(new ReportDataDefinitionDto(procKeyA)))))
        .isEqualTo(100.0);
  }

  @Test
  void shouldExcludeRunningInstancesFromIncidentRateDenominator() {
    // given: 1 completed agentic with incident, 1 completed agentic without,
    //        plus 5 running agentic instances (no incidents) — running must not count
    final ProcessInstanceDto withIncident =
        resolvedIncident(agenticInstanceWithTokens(PROC_KEY, 100L, 50L));
    final ProcessInstanceDto completedClean =
        agenticInstanceWithTokens(PROC_KEY, 100L, 50L).build();
    final List<ProcessInstanceDto> runningInstances =
        repeat(
            5,
            () ->
                agenticInstanceWithTokens(PROC_KEY, 100L, 50L)
                    .state(ProcessInstanceConstants.ACTIVE_STATE)
                    .endDate(null)
                    .duration(null)
                    .build());

    persistProcessInstances(
        java.util.stream.Stream.concat(
                java.util.stream.Stream.of(withIncident, completedClean), runningInstances.stream())
            .toList());

    // denominator = 2 completed agentic instances (not 7); numerator = 1 → 50%
    assertThat(reports.evaluateNumber(KPI_INCIDENT_RATE_REPORT_ID, noExtraFilters()))
        .isEqualTo(50.0);
  }

  @Test
  void shouldExcludeNonAgenticInstancesFromIncidentRateDenominator() {
    // given: 1 completed agentic with incident, 1 completed agentic without,
    //        plus 8 completed non-agentic instances — non-agentic must not count
    final ProcessInstanceDto withIncident =
        resolvedIncident(agenticInstanceWithTokens(PROC_KEY, 100L, 50L));
    final ProcessInstanceDto completedAgentic =
        agenticInstanceWithTokens(PROC_KEY, 100L, 50L).build();
    final List<ProcessInstanceDto> nonAgenticInstances =
        repeat(8, () -> completedInstance(PROC_KEY).build());

    persistProcessInstances(
        java.util.stream.Stream.concat(
                java.util.stream.Stream.of(withIncident, completedAgentic),
                nonAgenticInstances.stream())
            .toList());

    // denominator = 2 completed agentic instances (not 10); numerator = 1 → 50%
    assertThat(reports.evaluateNumber(KPI_INCIDENT_RATE_REPORT_ID, noExtraFilters()))
        .isEqualTo(50.0);
  }

  @Test
  void shouldScopeBothNumeratorAndDenominatorByDateFilter() {
    // given: within window — 1 agentic with incident, 1 agentic without
    //        outside window — 10 agentic with incidents (must not affect either side)
    final ProcessInstanceDto recentWithIncident =
        resolvedIncident(
            agenticInstanceWithTokens(PROC_KEY, 100L, 50L)
                .startDate(OffsetDateTime.now().minusHours(3))
                .endDate(OffsetDateTime.now().minusHours(2)));
    final ProcessInstanceDto recentClean =
        agenticInstanceWithTokens(PROC_KEY, 100L, 50L)
            .startDate(OffsetDateTime.now().minusHours(3))
            .endDate(OffsetDateTime.now().minusHours(2))
            .build();
    final List<ProcessInstanceDto> oldWithIncidents =
        repeat(
            10,
            () ->
                resolvedIncident(
                    agenticInstanceWithTokens(PROC_KEY, 100L, 50L)
                        .startDate(OffsetDateTime.now().minusDays(10))
                        .endDate(OffsetDateTime.now().minusDays(9))));

    persistProcessInstances(
        java.util.stream.Stream.concat(
                java.util.stream.Stream.of(recentWithIncident, recentClean),
                oldWithIncidents.stream())
            .toList());

    // denominator = 2 (in-window completed agentic); numerator = 1 → 50%
    assertThat(
            reports.evaluateNumber(
                KPI_INCIDENT_RATE_REPORT_ID, rollingEndDateFilter(1L, DateUnit.DAYS)))
        .isEqualTo(50.0);
  }

  @Test
  void shouldScopeBothNumeratorAndDenominatorByDefinitionFilter() {
    // given: procKeyA — 1 with incident, 3 without → scoped rate = 25%
    //        procKeyB — 5 all with incidents → must not inflate the procKeyA denominator
    final String procKeyA = "proc-scope-a";
    final String procKeyB = "proc-scope-b";

    final ProcessInstanceDto aWithIncident =
        resolvedIncident(agenticInstanceWithTokens(procKeyA, 100L, 50L));
    final List<ProcessInstanceDto> aClean =
        repeat(3, () -> agenticInstanceWithTokens(procKeyA, 100L, 50L).build());
    final List<ProcessInstanceDto> bWithIncidents =
        repeat(5, () -> resolvedIncident(agenticInstanceWithTokens(procKeyB, 100L, 50L)));

    persistProcessInstances(
        java.util.stream.Stream.concat(
                java.util.stream.Stream.concat(
                    java.util.stream.Stream.of(aWithIncident), aClean.stream()),
                bWithIncidents.stream())
            .toList());

    // denominator = 4 procKeyA instances (not 9); numerator = 1 → 25%
    assertThat(
            reports.evaluateNumber(
                KPI_INCIDENT_RATE_REPORT_ID,
                withDefinitions(List.of(new ReportDataDefinitionDto(procKeyA)))))
        .isEqualTo(25.0);
  }

  @Test
  void shouldComputeFailureRatePerProcessVersion() {
    // given version 1: 2 completed agentic instances, 1 with a resolved incident → 50%
    final ProcessInstanceDto v1WithIncident =
        resolvedIncident(agenticInstance(PROC_KEY, "1", 100L, 50L));
    final ProcessInstanceDto v1Clean = agenticInstance(PROC_KEY, "1", 100L, 50L).build();
    // version 2: 4 completed agentic instances, 1 with a resolved incident → 25%
    final ProcessInstanceDto v2WithIncident =
        resolvedIncident(agenticInstance(PROC_KEY, "2", 100L, 50L));
    final List<ProcessInstanceDto> v2Clean =
        repeat(3, () -> agenticInstance(PROC_KEY, "2", 100L, 50L).build());

    persistProcessInstances(
        Stream.concat(Stream.of(v1WithIncident, v1Clean, v2WithIncident), v2Clean.stream())
            .toList());

    // when
    final List<MapResultEntryDto> buckets =
        reports.evaluateMapData(FAILURE_RATE_BY_VERSION_REPORT_ID);

    // then
    assertThat(rateForVersion(buckets, "1")).isEqualTo(50.0);
    assertThat(rateForVersion(buckets, "2")).isEqualTo(25.0);
  }

  @Test
  void shouldScopeDenominatorPerVersionBucket() {
    // given version 1: 1 completed agentic with incident, 1 completed agentic clean → 50%
    final ProcessInstanceDto withIncident =
        resolvedIncident(agenticInstance(PROC_KEY, "1", 100L, 50L));
    final ProcessInstanceDto clean = agenticInstance(PROC_KEY, "1", 100L, 50L).build();
    // running agentic instances on the same version — must be excluded from the denominator
    final List<ProcessInstanceDto> running =
        repeat(
            5,
            () ->
                agenticInstance(PROC_KEY, "1", 100L, 50L)
                    .state(ProcessInstanceConstants.ACTIVE_STATE)
                    .endDate(null)
                    .duration(null)
                    .build());
    // completed non-agentic instances on the same version — must be excluded from the denominator
    final List<ProcessInstanceDto> nonAgentic =
        repeat(8, () -> completedInstance(PROC_KEY).processDefinitionVersion("1").build());

    persistProcessInstances(
        Stream.of(List.of(withIncident, clean), running, nonAgentic)
            .flatMap(List::stream)
            .toList());

    // when
    final List<MapResultEntryDto> buckets =
        reports.evaluateMapData(FAILURE_RATE_BY_VERSION_REPORT_ID);

    // then denominator = 2 completed agentic instances (not 15); numerator = 1 → 50%
    assertThat(rateForVersion(buckets, "1")).isEqualTo(50.0);
  }

  @Test
  void shouldScopeFailureRatePerVersionByDateFilter() {
    // given version 1 in window: 1 with incident, 1 clean → 50%
    final ProcessInstanceDto recentWithIncident =
        resolvedIncident(
            agenticInstance(PROC_KEY, "1", 100L, 50L)
                .startDate(OffsetDateTime.now().minusHours(3))
                .endDate(OffsetDateTime.now().minusHours(2)));
    final ProcessInstanceDto recentClean =
        agenticInstance(PROC_KEY, "1", 100L, 50L)
            .startDate(OffsetDateTime.now().minusHours(3))
            .endDate(OffsetDateTime.now().minusHours(2))
            .build();
    // version 1 out of window: 10 with incidents — must not affect the bucket
    final List<ProcessInstanceDto> oldWithIncidents =
        repeat(
            10,
            () ->
                resolvedIncident(
                    agenticInstance(PROC_KEY, "1", 100L, 50L)
                        .startDate(OffsetDateTime.now().minusDays(10))
                        .endDate(OffsetDateTime.now().minusDays(9))));

    persistProcessInstances(
        Stream.concat(Stream.of(recentWithIncident, recentClean), oldWithIncidents.stream())
            .toList());

    // when
    final List<MapResultEntryDto> buckets =
        reports.evaluateMapData(
            FAILURE_RATE_BY_VERSION_REPORT_ID, rollingEndDateFilter(1L, DateUnit.DAYS));

    // then only the 2 in-window instances count → 1/2 = 50%
    assertThat(rateForVersion(buckets, "1")).isEqualTo(50.0);
  }

  @Test
  void shouldReturnOnlyScopedProcessVersionsWhenDefinitionFilterApplied() {
    final String procKeyA = "proc-fr-a";
    final String procKeyB = "proc-fr-b";

    // procKeyA version 1: 2 instances, 1 with incident → 50%
    final ProcessInstanceDto aV1WithIncident =
        resolvedIncident(agenticInstance(procKeyA, "1", 100L, 50L));
    final ProcessInstanceDto aV1Clean = agenticInstance(procKeyA, "1", 100L, 50L).build();
    // procKeyA version 2: 4 instances, 1 with incident → 25%
    final ProcessInstanceDto aV2WithIncident =
        resolvedIncident(agenticInstance(procKeyA, "2", 100L, 50L));
    final List<ProcessInstanceDto> aV2Clean =
        repeat(3, () -> agenticInstance(procKeyA, "2", 100L, 50L).build());
    // procKeyB version 1: 5 instances all with incidents — must not inflate procKeyA's version 1
    final List<ProcessInstanceDto> bV1WithIncidents =
        repeat(5, () -> resolvedIncident(agenticInstance(procKeyB, "1", 100L, 50L)));

    persistProcessInstances(
        Stream.of(List.of(aV1WithIncident, aV1Clean, aV2WithIncident), aV2Clean, bV1WithIncidents)
            .flatMap(List::stream)
            .toList());

    // when
    final List<MapResultEntryDto> buckets =
        reports.evaluateMapData(
            FAILURE_RATE_BY_VERSION_REPORT_ID,
            withDefinitions(List.of(new ReportDataDefinitionDto(procKeyA))));

    // then scope to procKeyA → only its versions appear, each with its own scoped rate
    assertThat(buckets).extracting(MapResultEntryDto::getKey).containsExactlyInAnyOrder("1", "2");
    assertThat(rateForVersion(buckets, "1")).isEqualTo(50.0);
    assertThat(rateForVersion(buckets, "2")).isEqualTo(25.0);
  }

  @Test
  void shouldReturnNoErrorWhenNoCompletedAgenticInstancesMatch() {
    // given only running agentic and completed non-agentic instances — nothing to group
    final ProcessInstanceDto running =
        agenticInstance(PROC_KEY, "1", 100L, 50L)
            .state(ProcessInstanceConstants.ACTIVE_STATE)
            .endDate(null)
            .duration(null)
            .build();
    final ProcessInstanceDto nonAgentic = completedInstance(PROC_KEY).build();

    persistProcessInstances(List.of(running, nonAgentic));

    // when
    final List<MapResultEntryDto> buckets =
        reports.evaluateMapData(FAILURE_RATE_BY_VERSION_REPORT_ID);

    // then evaluation succeeds with empty buckets (no NaN, no exception)
    assertThat(buckets).isEmpty();
  }
}
