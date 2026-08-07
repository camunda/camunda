/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.writer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueTargetDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.TargetValueUnit;
import io.camunda.optimize.service.db.repository.BusinessValueTargetRepository;
import io.camunda.optimize.service.util.importing.ZeebeConstants;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class BusinessValueTargetWriterTest {

  private BusinessValueTargetRepository repository;
  private BusinessValueTargetWriter writer;

  @BeforeEach
  void setUp() {
    repository = mock(BusinessValueTargetRepository.class);
    writer = new BusinessValueTargetWriter(repository);
  }

  @Test
  void shouldDelegateValidTargetToRepository() {
    // given
    final BusinessValueTargetDto target = validTarget();

    // when
    writer.upsertTarget(target);

    // then
    verify(repository).upsert(target);
  }

  @Test
  void shouldRejectNullTarget() {
    assertThatThrownBy(() -> writer.upsertTarget(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("target");
    verifyNoInteractions(repository);
  }

  @Test
  void shouldRejectNullTenantId() {
    // given
    final BusinessValueTargetDto target = validTarget();
    target.setTenantId(null);

    // when + then
    assertThatThrownBy(() -> writer.upsertTarget(target))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenantId");
    verifyNoInteractions(repository);
  }

  @Test
  void shouldRejectBlankTenantId() {
    // given
    final BusinessValueTargetDto target = validTarget();
    target.setTenantId("   ");

    // when + then
    assertThatThrownBy(() -> writer.upsertTarget(target))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenantId");
    verifyNoInteractions(repository);
  }

  @Test
  void shouldRejectEmptyTenantId() {
    // given
    final BusinessValueTargetDto target = validTarget();
    target.setTenantId("");

    // when + then
    assertThatThrownBy(() -> writer.upsertTarget(target))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenantId");
    verifyNoInteractions(repository);
  }

  @Test
  void shouldRejectNegativeCycleTimeMillis() {
    // given a negative duration is not a valid target
    final BusinessValueTargetDto target = validTarget();
    target.setCycleTimeTargetMillis(-1L);

    // when + then
    assertThatThrownBy(() -> writer.upsertTarget(target))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cycleTimeTargetMillis");
    verifyNoInteractions(repository);
  }

  @Test
  void shouldAcceptZeroCycleTimeMillis() {
    // given zero is a valid (albeit tight) target
    final BusinessValueTargetDto target = validTarget();
    target.setCycleTimeTargetMillis(0L);

    // when
    writer.upsertTarget(target);

    // then
    verify(repository).upsert(target);
  }

  @Test
  void shouldRejectBlankProcessDefinitionKey() {
    // given
    final BusinessValueTargetDto target = validTarget();
    target.setProcessDefinitionKey("");

    // when + then
    assertThatThrownBy(() -> writer.upsertTarget(target))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("processDefinitionKey");
    verifyNoInteractions(repository);
  }

  @ParameterizedTest
  @EnumSource(
      value = TargetValueUnit.class,
      names = {"WEEKS", "MONTHS", "YEARS"})
  void shouldRejectUnsupportedCycleTimeUnit(final TargetValueUnit unsupported) {
    // given
    final BusinessValueTargetDto target = validTarget();
    target.setCycleTimeTargetUnit(unsupported);

    // when + then
    assertThatThrownBy(() -> writer.upsertTarget(target))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cycleTimeTargetUnit");
    verifyNoInteractions(repository);
  }

  @ParameterizedTest
  @EnumSource(
      value = TargetValueUnit.class,
      names = {"MILLIS", "SECONDS", "MINUTES", "HOURS", "DAYS"})
  void shouldAcceptSupportedCycleTimeUnit(final TargetValueUnit supported) {
    // given
    final BusinessValueTargetDto target = validTarget();
    target.setCycleTimeTargetUnit(supported);

    // when
    writer.upsertTarget(target);

    // then
    verify(repository).upsert(target);
  }

  @Test
  void shouldAcceptNullCycleTimeTargetUnit() {
    // given
    final BusinessValueTargetDto target = validTarget();
    target.setCycleTimeTargetMillis(null);
    target.setCycleTimeTargetUnit(null);

    // when
    writer.upsertTarget(target);

    // then
    verify(repository).upsert(target);
  }

  @Test
  void shouldRejectMillisWithoutUnit() {
    // given a duration value with no unit to interpret it
    final BusinessValueTargetDto target = validTarget();
    target.setCycleTimeTargetUnit(null);

    // when + then
    assertThatThrownBy(() -> writer.upsertTarget(target))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cycleTimeTargetMillis")
        .hasMessageContaining("cycleTimeTargetUnit");
    verifyNoInteractions(repository);
  }

  @Test
  void shouldRejectUnitWithoutMillis() {
    // given a unit with no numeric duration
    final BusinessValueTargetDto target = validTarget();
    target.setCycleTimeTargetMillis(null);

    // when + then
    assertThatThrownBy(() -> writer.upsertTarget(target))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cycleTimeTargetMillis")
        .hasMessageContaining("cycleTimeTargetUnit");
    verifyNoInteractions(repository);
  }

  @Test
  void shouldRejectAutomationRateOutOfRange() {
    // given
    final BusinessValueTargetDto target = validTarget();
    target.setAutomationRateTargetPct(101);

    // when + then
    assertThatThrownBy(() -> writer.upsertTarget(target))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("automationRateTargetPct");
    verifyNoInteractions(repository);
  }

  @Test
  void shouldAcceptAutomationRateAtBoundaries() {
    // given
    final BusinessValueTargetDto lower = validTarget();
    lower.setAutomationRateTargetPct(0);
    final BusinessValueTargetDto upper = validTarget();
    upper.setAutomationRateTargetPct(100);

    // when
    writer.upsertTarget(lower);
    writer.upsertTarget(upper);

    // then
    verify(repository).upsert(lower);
    verify(repository).upsert(upper);
  }

  private BusinessValueTargetDto validTarget() {
    final BusinessValueTargetDto dto = new BusinessValueTargetDto();
    dto.setProcessDefinitionKey("invoice-automation");
    dto.setTenantId(ZeebeConstants.ZEEBE_DEFAULT_TENANT_ID);
    dto.setCycleTimeTargetMillis(28_800_000L);
    dto.setCycleTimeTargetUnit(TargetValueUnit.HOURS);
    dto.setAutomationRateTargetPct(85);
    dto.setUpdatedAt(OffsetDateTime.parse("2026-08-05T10:15:00Z"));
    dto.setUpdatedBy("sherrin@camunda.com");
    assertThat(dto.getTenantId()).isNotNull();
    return dto;
  }
}
