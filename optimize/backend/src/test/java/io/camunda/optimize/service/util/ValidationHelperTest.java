/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util;

import io.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisRequestDto;
import io.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.instance.FixedDateFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.AssigneeFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.InstanceStartDateFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.data.IdentityLinkFilterDataDto;
import io.camunda.optimize.service.exceptions.OptimizeValidationException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class ValidationHelperTest {

  @Test
  void shouldBeSuccessfullyValidatedWhenDtoHasValidFieldValues() {
    // given
    final BranchAnalysisRequestDto dto = new BranchAnalysisRequestDto();
    dto.setFilter(
        List.of(
            new AssigneeFilterDto(
                new IdentityLinkFilterDataDto(MembershipFilterOperator.IN, List.of()))));
    dto.setEnd("3");
    dto.setGateway("3");
    dto.setProcessDefinitionKey("3");
    dto.setProcessDefinitionVersion("3");

    // then
    ValidationHelper.validate(dto);
  }

  @Test
  void shouldThrowExceptionWhenDtoIsInvalid() {
    // given
    final BranchAnalysisRequestDto dto = new BranchAnalysisRequestDto();

    // when
    final Throwable thrown = Assertions.catchThrowable(() -> ValidationHelper.validate(dto));

    // then
    Assertions.assertThat(thrown)
        .isInstanceOf(OptimizeValidationException.class)
        .hasMessageContaining("gateway activity id is not allowed to be empty or null");
  }

  @Test
  void shouldThrowExceptionWhenTargetIsNull() {
    // given
    final String fieldName = "testField";
    final Object target = null;

    // when
    final Throwable thrown =
        Assertions.catchThrowable(() -> ValidationHelper.ensureNotEmpty(fieldName, target));

    // then
    Assertions.assertThat(thrown)
        .isInstanceOf(OptimizeValidationException.class)
        .hasMessageContaining("testField is not allowed to be empty or null");
  }

  @Test
  void shouldThrowExceptionWhenCollectionIsEmpty() {
    // given
    final String fieldName = "testCollection";
    final List<String> collection = Collections.emptyList();

    // when
    final Throwable thrown =
        Assertions.catchThrowable(
            () -> ValidationHelper.ensureCollectionNotEmpty(fieldName, collection));

    // then
    Assertions.assertThat(thrown)
        .isInstanceOf(OptimizeValidationException.class)
        .hasMessageContaining("testCollection is not allowed to be empty or null");
  }

  @Test
  void shouldRejectEntityNameExceedingMaxLength() {
    // given
    final String name = "a".repeat(ValidationHelper.DEFAULT_MAX_ENTITY_NAME_LENGTH + 1);

    // when
    final Throwable thrown =
        Assertions.catchThrowable(() -> ValidationHelper.validateEntityName("Report", name, null));

    // then
    Assertions.assertThat(thrown)
        .isInstanceOf(OptimizeValidationException.class)
        .hasMessageContaining("Report names cannot be greater than 32768 characters");
  }

  @Test
  void shouldAcceptEntityNameAtExactlyMaxLength() {
    // given
    final String name = "a".repeat(ValidationHelper.DEFAULT_MAX_ENTITY_NAME_LENGTH);

    // then
    ValidationHelper.validateEntityName("Report", name, null);
  }

  @Test
  void shouldAcceptNullEntityNameSoDefaultNamesStillApply() {
    // then
    ValidationHelper.validateEntityName("Dashboard", null, null);
  }

  @Test
  void shouldEnforceConfiguredLimitInsteadOfDefault() {
    // given
    final String name = "a".repeat(257);

    // when
    final Throwable thrown =
        Assertions.catchThrowable(
            () -> ValidationHelper.validateEntityName("Collection", name, 256));

    // then
    Assertions.assertThat(thrown)
        .isInstanceOf(OptimizeValidationException.class)
        .hasMessageContaining("Collection names cannot be greater than 256 characters");
  }

  @Test
  void shouldCountCharactersNotUtf8Bytes() {
    // given a name of 4-byte characters that is within the character limit but not the byte limit
    final String name = "\uD83D\uDE00".repeat(100);

    // then
    ValidationHelper.validateEntityName("Report", name, 256);
  }

  @Test
  void shouldClampGeneratedNameToConfiguredLimit() {
    // given
    final String generated = "a".repeat(300);

    // when
    final String clamped = ValidationHelper.clampGeneratedEntityName(generated, 256);

    // then
    Assertions.assertThat(clamped).hasSize(256);
  }

  @Test
  void shouldNotSplitSurrogatePairWhenClamping() {
    // given a name where the limit falls exactly between the two halves of a surrogate pair
    final String emoji = "\uD83D\uDE00";
    final String generated = "a" + emoji.repeat(10);

    // when
    final String clamped = ValidationHelper.clampGeneratedEntityName(generated, 2);

    // then the trailing half-character is dropped rather than persisted as invalid Unicode
    Assertions.assertThat(clamped).isEqualTo("a");
  }

  @Test
  void shouldKeepWholeSurrogatePairWhenClampingLandsAfterIt() {
    // given
    final String emoji = "\uD83D\uDE00";
    final String generated = emoji.repeat(10);

    // when the limit falls on a pair boundary rather than inside a pair
    final String clamped = ValidationHelper.clampGeneratedEntityName(generated, 4);

    // then
    Assertions.assertThat(clamped).isEqualTo(emoji.repeat(2));
  }

  @Test
  void shouldFallBackToDefaultWhenConfiguredLimitIsNotPositive() {
    // given a misconfigured limit that would otherwise reject every name and break clamping
    final String name = "a".repeat(300);

    // then
    ValidationHelper.validateEntityName("Report", name, 0);
    ValidationHelper.validateEntityName("Report", name, -1);
    Assertions.assertThat(ValidationHelper.clampGeneratedEntityName(name, 0)).isEqualTo(name);
  }

  @Test
  void shouldLeaveGeneratedNameWithinLimitUntouched() {
    // given
    final String generated = "a".repeat(100);

    // then
    Assertions.assertThat(ValidationHelper.clampGeneratedEntityName(generated, 256))
        .isEqualTo(generated);
  }

  @Test
  void shouldValidateFiltersCorrectly() {
    // given
    final ProcessFilterDto<DateFilterDataDto<?>> validFilter = new InstanceStartDateFilterDto();
    validFilter.setFilterLevel(FilterApplicationLevel.INSTANCE);
    validFilter.setData(new FixedDateFilterDataDto(null, null));

    // when
    final Throwable thrown =
        Assertions.catchThrowable(
            () -> ValidationHelper.validateProcessFilters(List.of(validFilter)));

    // then
    Assertions.assertThat(thrown)
        .isInstanceOf(OptimizeValidationException.class)
        .hasMessageContaining(
            "start date filter  at least one sub field not allowed to be empty or null");
  }

  @Test
  void shouldThrowExceptionWhenAggregationIsInvalid() {
    // given
    final AggregationDto invalidAggregation = new AggregationDto();
    invalidAggregation.setType(AggregationType.PERCENTILE);
    invalidAggregation.setValue(150.0);

    // when
    final Throwable thrown =
        Assertions.catchThrowable(
            () -> ValidationHelper.validateAggregationTypes(Set.of(invalidAggregation)));

    // then
    Assertions.assertThat(thrown)
        .isInstanceOf(OptimizeValidationException.class)
        .hasMessageContaining("Percentile aggregation values be between 0 and 100");
  }

  @Test
  void shouldReturnFalseForInvalidDto() {
    // given
    final ReportDataDto invalidDto = null;

    // when
    final boolean result = ValidationHelper.isValid(invalidDto);

    // then
    Assertions.assertThat(result).isFalse();
  }
}
