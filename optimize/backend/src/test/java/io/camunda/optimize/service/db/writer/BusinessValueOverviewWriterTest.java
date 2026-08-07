/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.writer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewDto;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewDto.AutomationRateBlock;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewDto.CycleTimeBlock;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewDto.MetricRange;
import io.camunda.optimize.service.db.repository.BusinessValueOverviewRepository;
import io.camunda.optimize.service.util.importing.ZeebeConstants;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BusinessValueOverviewWriterTest {

  private BusinessValueOverviewRepository repository;
  private BusinessValueOverviewWriter writer;

  @BeforeEach
  void setUp() {
    repository = mock(BusinessValueOverviewRepository.class);
    writer = new BusinessValueOverviewWriter(repository);
  }

  @Test
  void shouldForwardSchedulerUpsertWithoutForcedRefresh() {
    // given
    final List<BusinessValueOverviewDto> rows = List.of(validRow());

    // when
    writer.bulkUpsertFromScheduler(rows);

    // then
    verify(repository).bulkUpsert(rows, false);
  }

  @Test
  void shouldForwardTargetWriteUpsertWithForcedRefresh() {
    // given
    final List<BusinessValueOverviewDto> rows = List.of(validRow());

    // when
    writer.bulkUpsertFromTargetWrite(rows);

    // then
    verify(repository).bulkUpsert(rows, true);
  }

  @Test
  void shouldAcceptEmptyRowList() {
    // given
    final List<BusinessValueOverviewDto> empty = List.of();

    // when
    writer.bulkUpsertFromScheduler(empty);

    // then
    verify(repository).bulkUpsert(empty, false);
  }

  @Test
  void shouldRejectNullRowList() {
    assertThatThrownBy(() -> writer.bulkUpsertFromScheduler(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("rows");
    verifyNoInteractions(repository);
  }

  @Test
  void shouldRejectNullRowInList() {
    // guards the per-element null check inside validateAll; the "null list" case above only
    // covers the outer guard
    assertThatThrownBy(() -> writer.bulkUpsertFromScheduler(Arrays.asList(validRow(), null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("row must not be null");
    verifyNoInteractions(repository);
  }

  @Test
  void shouldRejectRowMissingTenantId() {
    // given
    final BusinessValueOverviewDto row = validRow();
    row.setTenantId(null);

    // when + then
    assertThatThrownBy(() -> writer.bulkUpsertFromScheduler(List.of(row)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenantId");
    verifyNoInteractions(repository);
  }

  @Test
  void shouldRejectRowWithBlankTenantId() {
    // given a whitespace-only tenantId — nonsensical, would produce a garbage composite doc id
    final BusinessValueOverviewDto row = validRow();
    row.setTenantId("   ");

    // when + then
    assertThatThrownBy(() -> writer.bulkUpsertFromScheduler(List.of(row)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenantId");
    verifyNoInteractions(repository);
  }

  @Test
  void shouldRejectRowMissingProcessDefinitionKey() {
    // given
    final BusinessValueOverviewDto row = validRow();
    row.setProcessDefinitionKey("");

    // when + then
    assertThatThrownBy(() -> writer.bulkUpsertFromScheduler(List.of(row)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("processDefinitionKey");
    verifyNoInteractions(repository);
  }

  @Test
  void shouldRejectRowMissingMetricRange() {
    // given
    final BusinessValueOverviewDto row = validRow();
    row.setMetricRange(null);

    // when + then
    assertThatThrownBy(() -> writer.bulkUpsertFromScheduler(List.of(row)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("metricRange");
    verifyNoInteractions(repository);
  }

  @Test
  void shouldRejectRowMissingLastComputedAt() {
    // given
    final BusinessValueOverviewDto row = validRow();
    row.setLastComputedAt(null);

    // when + then
    assertThatThrownBy(() -> writer.bulkUpsertFromScheduler(List.of(row)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("lastComputedAt");
    verifyNoInteractions(repository);
  }

  @Test
  void shouldRejectInconsistentTargetsSet() {
    // given a row that claims 2 targets set but only 1 target is actually non-null
    final BusinessValueOverviewDto row = validRow();
    row.setAutomationRate(new AutomationRateBlock(null, null, null));
    row.setTargetsSet(2);

    // when + then
    assertThatThrownBy(() -> writer.bulkUpsertFromScheduler(List.of(row)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("targetsSet");
    verifyNoInteractions(repository);
  }

  @Test
  void shouldRejectHasAnyTargetInconsistentWithTargetsSet() {
    // given a row that has no targets but hasAnyTarget=true
    final BusinessValueOverviewDto row = validRow();
    row.setCycleTime(new CycleTimeBlock(14_472_000L, null, null));
    row.setAutomationRate(new AutomationRateBlock(72.4, null, null));
    row.setTargetsSet(0);
    row.setTargetsMet(0);
    row.setHasAnyTarget(true);

    // when + then
    assertThatThrownBy(() -> writer.bulkUpsertFromScheduler(List.of(row)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("hasAnyTarget");
    verifyNoInteractions(repository);
  }

  @Test
  void shouldRejectTargetsMetExceedingTargetsSet() {
    // given a row where targetsMet > targetsSet — impossible by definition
    final BusinessValueOverviewDto row = validRow();
    row.setTargetsMet(3);

    // when + then
    assertThatThrownBy(() -> writer.bulkUpsertFromScheduler(List.of(row)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("targetsMet");
    verifyNoInteractions(repository);
  }

  @Test
  void shouldRejectMetSetWhenValueOrTargetIsNull() {
    // given a KPI block with null value but a non-null met flag; other counts kept consistent so
    // the block-invariant check is what fires
    final BusinessValueOverviewDto row = validRow();
    row.setCycleTime(new CycleTimeBlock(null, 11_520_000L, true));
    row.setAutomationRate(null);
    row.setHasAnyTarget(true);
    row.setTargetsSet(1);
    row.setTargetsMet(1);

    // when + then
    assertThatThrownBy(() -> writer.bulkUpsertFromScheduler(List.of(row)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cycleTime.met");
    verifyNoInteractions(repository);
  }

  @Test
  void shouldRejectMetNullWhenValueAndTargetAreBothSet() {
    // given a KPI block with both value and target but no met verdict; counts kept consistent so
    // the block-invariant check is what fires
    final BusinessValueOverviewDto row = validRow();
    row.setAutomationRate(new AutomationRateBlock(72.4, 85, null));
    row.setTargetsMet(0);

    // when + then
    assertThatThrownBy(() -> writer.bulkUpsertFromScheduler(List.of(row)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("automationRate.met");
    verifyNoInteractions(repository);
  }

  @Test
  void shouldAcceptRowWithNoTargetsSet() {
    // given an empty-targets row (both blocks null) — valid on first scheduler tick
    final BusinessValueOverviewDto row = validRow();
    row.setCycleTime(null);
    row.setAutomationRate(null);
    row.setHasAnyTarget(false);
    row.setTargetsSet(0);
    row.setTargetsMet(0);

    // when
    writer.bulkUpsertFromScheduler(List.of(row));

    // then
    verify(repository).bulkUpsert(List.of(row), false);
  }

  private BusinessValueOverviewDto validRow() {
    return new BusinessValueOverviewDto(
        ZeebeConstants.ZEEBE_DEFAULT_TENANT_ID,
        "invoice-automation",
        "Invoice Automation",
        MetricRange.THIRTY_DAYS,
        OffsetDateTime.parse("2026-08-05T04:00:15Z"),
        new CycleTimeBlock(14_472_000L, 11_520_000L, false),
        new AutomationRateBlock(72.4, 85, false),
        true,
        2,
        0);
  }
}
