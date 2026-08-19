/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.businessvalue;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.TargetValueUnit;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Locks the JSR-380 constraints declared on {@link BusinessValueTargetUpsertRequestDto} and its
 * nested {@link CycleTimeTargetDto}. The constraints are the caller-facing 400 gate — Optimize's
 * {@code BeanConstraintViolationExceptionMapper} turns any violation surfaced here into a 400 in
 * the HTTP layer, so a silent removal of one of these annotations would let malformed input reach
 * the writer as an unchecked 500.
 */
class BusinessValueTargetDtoValidationTest {

  private static ValidatorFactory factory;
  private static Validator validator;

  @BeforeAll
  static void setUpValidator() {
    factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  static void tearDownValidator() {
    factory.close();
  }

  // --- happy path: fully populated request passes ---

  @Test
  void shouldPassValidationForFullyPopulatedRequest() {
    // given
    final BusinessValueTargetUpsertRequestDto request =
        new BusinessValueTargetUpsertRequestDto(
            new CycleTimeTargetDto(8L, TargetValueUnit.HOURS, null), 85);

    // when
    final Set<ConstraintViolation<BusinessValueTargetUpsertRequestDto>> violations =
        validator.validate(request);

    // then
    assertThat(violations).isEmpty();
  }

  @Test
  void shouldPassValidationForClearRequest() {
    // given — both fields null is a valid "clear both" upsert
    final BusinessValueTargetUpsertRequestDto request =
        new BusinessValueTargetUpsertRequestDto(null, null);

    // when
    final Set<ConstraintViolation<BusinessValueTargetUpsertRequestDto>> violations =
        validator.validate(request);

    // then
    assertThat(violations).isEmpty();
  }

  // --- automationRateTargetPct boundaries ---

  @ParameterizedTest(name = "shouldPassAutomationRateBoundary[{0}]")
  @ValueSource(ints = {0, 1, 50, 99, 100})
  void shouldPassValidationForAutomationRateWithinRange(final int pct) {
    // given
    final BusinessValueTargetUpsertRequestDto request =
        new BusinessValueTargetUpsertRequestDto(null, pct);

    // when
    final Set<ConstraintViolation<BusinessValueTargetUpsertRequestDto>> violations =
        validator.validate(request);

    // then
    assertThat(violations).isEmpty();
  }

  @ParameterizedTest(name = "shouldRejectAutomationRateOutOfRange[{0}]")
  @ValueSource(ints = {-1, -100, 101, 200, Integer.MAX_VALUE, Integer.MIN_VALUE})
  void shouldRejectValidationForAutomationRateOutOfRange(final int pct) {
    // given
    final BusinessValueTargetUpsertRequestDto request =
        new BusinessValueTargetUpsertRequestDto(null, pct);

    // when
    final Set<ConstraintViolation<BusinessValueTargetUpsertRequestDto>> violations =
        validator.validate(request);

    // then
    assertThat(violations)
        .anySatisfy(
            v -> assertThat(v.getPropertyPath().toString()).isEqualTo("automationRateTargetPct"));
  }

  // --- cycleTimeTarget.value boundaries (cascaded via @Valid on the outer record) ---

  @ParameterizedTest(name = "shouldPassCycleTimeValueBoundary[{0}]")
  @ValueSource(longs = {0L, 1L, 24L, 999_999L, Long.MAX_VALUE})
  void shouldPassValidationForNonNegativeCycleTimeValue(final long value) {
    // given
    final BusinessValueTargetUpsertRequestDto request =
        new BusinessValueTargetUpsertRequestDto(
            new CycleTimeTargetDto(value, TargetValueUnit.HOURS, null), null);

    // when
    final Set<ConstraintViolation<BusinessValueTargetUpsertRequestDto>> violations =
        validator.validate(request);

    // then
    assertThat(violations).isEmpty();
  }

  @ParameterizedTest(name = "shouldRejectNegativeCycleTimeValue[{0}]")
  @ValueSource(longs = {-1L, -100L, Long.MIN_VALUE})
  void shouldRejectValidationForNegativeCycleTimeValue(final long value) {
    // given
    final BusinessValueTargetUpsertRequestDto request =
        new BusinessValueTargetUpsertRequestDto(
            new CycleTimeTargetDto(value, TargetValueUnit.HOURS, null), null);

    // when
    final Set<ConstraintViolation<BusinessValueTargetUpsertRequestDto>> violations =
        validator.validate(request);

    // then — the violation must point at the nested field, not the outer DTO
    assertThat(violations)
        .anySatisfy(
            v -> assertThat(v.getPropertyPath().toString()).isEqualTo("cycleTimeTarget.value"));
  }

  // --- null cycleTimeTarget skips cascade — no violation ---

  @Test
  void shouldPassValidationWhenCycleTimeTargetIsNull() {
    // given
    final BusinessValueTargetUpsertRequestDto request =
        new BusinessValueTargetUpsertRequestDto(null, 50);

    // when
    final Set<ConstraintViolation<BusinessValueTargetUpsertRequestDto>> violations =
        validator.validate(request);

    // then
    assertThat(violations).isEmpty();
  }

  // --- combined violations: both fields wrong at once ---

  @Test
  void shouldSurfaceMultipleViolationsAtOnce() {
    // given — negative value + out-of-range pct
    final BusinessValueTargetUpsertRequestDto request =
        new BusinessValueTargetUpsertRequestDto(
            new CycleTimeTargetDto(-5L, TargetValueUnit.HOURS, null), 250);

    // when
    final Set<ConstraintViolation<BusinessValueTargetUpsertRequestDto>> violations =
        validator.validate(request);

    // then
    assertThat(violations)
        .extracting(v -> v.getPropertyPath().toString())
        .contains("cycleTimeTarget.value", "automationRateTargetPct");
  }
}
