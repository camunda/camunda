/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.util;

import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_DATE_PARSING;
import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_INVALID_KEY_FORMAT;
import static org.assertj.core.api.Assertions.*;

import io.camunda.gateway.mapping.http.converters.CustomConverter;
import io.camunda.gateway.mapping.http.converters.ProcessInstanceStateConverter;
import io.camunda.gateway.protocol.model.AdvancedDateTimeFilter;
import io.camunda.gateway.protocol.model.AdvancedIntegerFilter;
import io.camunda.gateway.protocol.model.AdvancedStringFilter;
import io.camunda.gateway.protocol.model.BasicStringFilter;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.Operator;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AdvancedSearchFilterUtilTest {

  private static final List<List<Operation<Long>>> BASIC_LONG_OPERATIONS =
      List.of(
          List.of(Operation.eq(10L)),
          List.of(Operation.neq(1L)),
          List.of(Operation.exists(true)),
          List.of(Operation.exists(false)),
          List.of(Operation.in(5L, 10L)));

  private static final List<List<Operation<String>>> BASIC_STRING_OPERATIONS =
      List.of(
          List.of(Operation.eq("this")),
          List.of(Operation.neq("that")),
          List.of(Operation.exists(true)),
          List.of(Operation.exists(false)),
          List.of(Operation.in("this", "that")));

  private static final List<List<Operation<String>>> STRING_OPERATIONS =
      List.of(
          List.of(Operation.like("th%")),
          List.of(Operation.in("this", "that"), Operation.like("th%")));

  private static final List<List<Operation<OffsetDateTime>>> DATE_TIME_OPERATIONS =
      List.of(
          List.of(Operation.eq(OffsetDateTime.now())),
          List.of(Operation.neq(OffsetDateTime.now().withOffsetSameInstant(ZoneOffset.of("Z")))),
          List.of(Operation.exists(true)),
          List.of(Operation.exists(false)),
          List.of(Operation.gt(OffsetDateTime.now().minusHours(1))),
          List.of(Operation.gte(OffsetDateTime.now().minusHours(1))),
          List.of(Operation.lt(OffsetDateTime.now().minusHours(1))),
          List.of(Operation.lte(OffsetDateTime.now().minusHours(1))),
          List.of(
              Operation.gt(OffsetDateTime.now().minusHours(1)), Operation.lt(OffsetDateTime.now())),
          List.of(
              Operation.gte(OffsetDateTime.now().minusHours(1)),
              Operation.lte(OffsetDateTime.now())),
          List.of(Operation.in(OffsetDateTime.now(), OffsetDateTime.now().minusDays(1))));

  private static final List<List<Operation<Integer>>> BASIC_INTEGER_OPERATIONS =
      List.of(
          List.of(Operation.eq(10)),
          List.of(Operation.neq(1)),
          List.of(Operation.exists(true)),
          List.of(Operation.exists(false)),
          List.of(Operation.in(5, 10)));

  private static final List<List<Operation<Integer>>> INTEGER_OPERATIONS =
      List.of(
          List.of(Operation.gt(5)),
          List.of(Operation.gte(5)),
          List.of(Operation.lt(5)),
          List.of(Operation.lte(5)),
          List.of(Operation.gt(5), Operation.lt(10)),
          List.of(Operation.gte(5), Operation.lte(10)));

  @SuppressWarnings("unchecked")
  private static <F> F createFilterInstance(final Class<F> fClass) throws Exception {
    // Use the staged builder to create an empty filter instance.
    // Builder.builder() returns IBuild directly (no required fields for these filter types).
    // We invoke build() via the IBuild interface class (accessible) rather than the Impl class
    // (package-private) to avoid IllegalAccessException.
    final var builderClass = Class.forName(fClass.getName() + "$Builder");
    final var iBuildClass = Class.forName(fClass.getName() + "$Builder$IBuild");
    final var builderMethod = builderClass.getDeclaredMethod("builder");
    final var iBuild = builderMethod.invoke(null);
    final var buildMethod = iBuildClass.getMethod("build");
    return fClass.cast(buildMethod.invoke(iBuild));
  }

  private <F, T> F constructFilter(
      final Class<F> fClass, final Class<T> pClass, final List<Operation<T>> operations)
      throws Exception {
    final var filter = createFilterInstance(fClass);
    for (final Operation<T> op : operations) {
      final var operator = op.operator();
      final var methodName = "set$%s".formatted(StringUtils.capitalize(operator.getValue()));
      final var existsName = "set$%s".formatted(StringUtils.capitalize(Operator.EXISTS.getValue()));
      final var method =
          switch (operator) {
            case IN -> filter.getClass().getMethod(methodName, List.class);
            case EXISTS, NOT_EXISTS -> filter.getClass().getMethod(existsName, Boolean.class);
            default -> filter.getClass().getMethod(methodName, pClass);
          };
      method.setAccessible(true);
      switch (operator) {
        case IN -> method.invoke(filter, op.values());
        case EXISTS, NOT_EXISTS -> method.invoke(filter, operator.equals(Operator.EXISTS));
        default -> method.invoke(filter, op.value());
      }
    }
    return filter;
  }

  /**
   * Constructs a BasicStringFilter from Long operations by converting values to String (simulating
   * how JSON key fields like processInstanceKey arrive as strings).
   */
  private BasicStringFilter constructStringFilterFromLongOps(final List<Operation<Long>> operations)
      throws Exception {
    final List<Operation<String>> stringOps =
        operations.stream()
            .map(
                op ->
                    new Operation<>(
                        op.operator(),
                        op.values() != null
                            ? op.values().stream().map(String::valueOf).toList()
                            : null))
            .toList();
    return constructFilter(BasicStringFilter.class, String.class, stringOps);
  }

  private static Stream<List<Operation<Long>>> provideKeyOperationsParameters() {
    return BASIC_LONG_OPERATIONS.stream();
  }

  @ParameterizedTest
  @MethodSource("provideKeyOperationsParameters")
  void shouldMapKeyOperationsCorrectly(final List<Operation<Long>> operations) throws Exception {
    // given
    final var filter = constructStringFilterFromLongOps(operations);
    final var errors = new ArrayList<String>();

    // when
    final var actual = AdvancedSearchFilterUtil.mapToKeyOperations("key", errors).apply(filter);

    // then
    assertThat(errors).isEmpty();
    assertThat(actual).hasSize(operations.size());
    assertThat(actual).containsExactlyInAnyOrderElementsOf(operations);
  }

  private static Stream<Arguments> provideStringOperationsParameters() {
    final var streamBuilder = Stream.<Arguments>builder();
    BASIC_STRING_OPERATIONS.stream()
        .flatMap(
            ops ->
                Stream.of(
                    Arguments.of(BasicStringFilter.class, ops),
                    Arguments.of(AdvancedStringFilter.class, ops)))
        .forEach(streamBuilder::add);
    STRING_OPERATIONS.stream()
        .map(ops -> Arguments.of(AdvancedStringFilter.class, ops))
        .forEach(streamBuilder::add);
    return streamBuilder.build();
  }

  @ParameterizedTest
  @MethodSource("provideStringOperationsParameters")
  void shouldMapStringOperationsCorrectly(
      final Class<?> filterClass, final List<Operation<String>> operations) throws Exception {
    // given
    final var filter = constructFilter(filterClass, String.class, operations);

    // when
    final var actual = AdvancedSearchFilterUtil.mapToStringOperations().apply(filter);

    // then
    assertThat(actual).hasSize(operations.size());
    assertThat(actual).containsExactlyInAnyOrderElementsOf(operations);
  }

  private static Stream<List<Operation<Integer>>> provideIntegerOperationsParameters() {
    return Stream.concat(BASIC_INTEGER_OPERATIONS.stream(), INTEGER_OPERATIONS.stream());
  }

  @ParameterizedTest
  @MethodSource("provideIntegerOperationsParameters")
  void shouldMapIntegerOperationsCorrectly(final List<Operation<Integer>> operations)
      throws Exception {
    // given
    final var filter = constructFilter(AdvancedIntegerFilter.class, Integer.class, operations);
    final var errors = new ArrayList<String>();

    // when
    final var actual =
        AdvancedSearchFilterUtil.mapToIntegerOperations("retries", errors).apply(filter);

    // then
    assertThat(errors).isEmpty();
    assertThat(actual).hasSize(operations.size());
    assertThat(actual).containsExactlyInAnyOrderElementsOf(operations);
  }

  private static Stream<List<Operation<OffsetDateTime>>> provideDateTimeOperationsParameters() {
    return DATE_TIME_OPERATIONS.stream();
  }

  @ParameterizedTest
  @MethodSource("provideDateTimeOperationsParameters")
  void shouldMapDateTimeOperationsCorrectly(final List<Operation<OffsetDateTime>> operations)
      throws Exception {
    // given — AdvancedDateTimeFilter has String fields, so convert OffsetDateTime to String
    final List<Operation<String>> stringOps =
        operations.stream()
            .map(
                op ->
                    new Operation<>(
                        op.operator(),
                        op.values() != null
                            ? op.values().stream().map(OffsetDateTime::toString).toList()
                            : null))
            .toList();
    final var filter = constructFilter(AdvancedDateTimeFilter.class, String.class, stringOps);
    final var errors = new ArrayList<String>();

    // when
    final var actual =
        AdvancedSearchFilterUtil.mapToOffsetDateTimeOperations("date", errors).apply(filter);

    // then
    assertThat(errors).isEmpty();
    assertThat(actual).hasSize(operations.size());
    assertThat(actual).containsExactlyInAnyOrderElementsOf(operations);
  }

  @Test
  void shouldMapToStringOperationsFromInteger() {
    // given
    final var filter = AdvancedIntegerFilter.Builder.builder().build();
    filter.set$Eq(10);

    // when
    final var actual = AdvancedSearchFilterUtil.mapToStringOperations().apply(filter);

    // then
    assertThat(actual).hasSize(1);
    assertThat(actual.getFirst()).isEqualTo(Operation.eq("10"));
  }

  @Test
  void shouldMapToStringOperationsWithConverter() {
    // given
    final var filter = BasicStringFilter.Builder.builder().build();
    filter.set$Eq("ACTIVE");
    final var errors = new ArrayList<String>();

    // when
    final var actual =
        AdvancedSearchFilterUtil.mapToStringOperations(
                "state", errors, new ProcessInstanceStateConverter())
            .apply(filter);

    // then
    assertThat(errors).isEmpty();
    assertThat(actual).hasSize(1);
  }

  @Test
  void shouldCollectErrorForInvalidKeyValue() {
    // given
    final var filter = BasicStringFilter.Builder.builder().build();
    filter.set$Eq("abc");
    final var errors = new ArrayList<String>();

    // when
    final var actual =
        AdvancedSearchFilterUtil.mapToKeyOperations("processInstanceKey", errors).apply(filter);

    // then
    assertThat(errors)
        .containsExactly(ERROR_MESSAGE_INVALID_KEY_FORMAT.formatted("processInstanceKey", "abc"));
    assertThat(actual).isEmpty();
  }

  @Test
  void shouldCollectErrorForInvalidDateValue() {
    // given
    final var filter = AdvancedDateTimeFilter.Builder.builder().build();
    filter.set$Eq("not-a-date");
    final var errors = new ArrayList<String>();

    // when
    final var actual =
        AdvancedSearchFilterUtil.mapToOffsetDateTimeOperations("startDate", errors).apply(filter);

    // then
    assertThat(errors)
        .containsExactly(ERROR_MESSAGE_DATE_PARSING.formatted("startDate", "not-a-date"));
    assertThat(actual).isEmpty();
  }

  @Test
  void shouldCollectMultipleErrors() {
    // given
    final var filter1 = BasicStringFilter.Builder.builder().build();
    filter1.set$Eq("abc");
    final var filter2 = AdvancedDateTimeFilter.Builder.builder().build();
    filter2.set$Eq("not-a-date");
    final var errors = new ArrayList<String>();

    // when
    AdvancedSearchFilterUtil.mapToKeyOperations("processInstanceKey", errors).apply(filter1);
    AdvancedSearchFilterUtil.mapToOffsetDateTimeOperations("startDate", errors).apply(filter2);

    // then
    assertThat(errors).hasSize(2);
    assertThat(errors.get(0))
        .isEqualTo(ERROR_MESSAGE_INVALID_KEY_FORMAT.formatted("processInstanceKey", "abc"));
    assertThat(errors.get(1))
        .isEqualTo(ERROR_MESSAGE_DATE_PARSING.formatted("startDate", "not-a-date"));
  }

  @Test
  void shouldHandleExistsOperationInTypedMethods() {
    // given
    final var filter = BasicStringFilter.Builder.builder().build();
    filter.set$Exists(true);
    final var errors = new ArrayList<String>();

    // when
    final var actual = AdvancedSearchFilterUtil.mapToKeyOperations("key", errors).apply(filter);

    // then
    assertThat(errors).isEmpty();
    assertThat(actual).containsExactly(Operation.exists(true));
  }

  @Test
  void shouldCollectErrorForNonIntegerValue() {
    // given — BasicStringFilter has String fields, simulating a type mismatch
    final var filter = BasicStringFilter.Builder.builder().build();
    filter.set$Eq("notAnInteger");
    final var errors = new ArrayList<String>();

    // when
    final var actual =
        AdvancedSearchFilterUtil.mapToIntegerOperations("retries", errors).apply(filter);

    // then
    assertThat(errors)
        .containsExactly("The provided retries 'notAnInteger' is not a valid integer value.");
    assertThat(actual).isEmpty();
  }

  @Test
  void shouldCollectErrorWhenConverterThrows() {
    // given
    final var filter = BasicStringFilter.Builder.builder().build();
    filter.set$Eq("badValue");
    final var errors = new ArrayList<String>();
    final CustomConverter<String> failingConverter =
        new CustomConverter<>() {
          @Override
          public boolean canConvert(final Object value) {
            return true;
          }

          @Override
          public String convertValue(final Object value) {
            throw new IllegalArgumentException("conversion failed");
          }
        };

    // when
    final var actual =
        AdvancedSearchFilterUtil.mapToStringOperations("state", errors, failingConverter)
            .apply(filter);

    // then
    assertThat(errors)
        .containsExactly("The provided state 'badValue' is not valid: conversion failed");
    assertThat(actual).isEmpty();
  }

  @Test
  void shouldSkipInvalidValuesInListForKeyOperations() {
    // given
    final var filter = BasicStringFilter.Builder.builder().build();
    filter.set$In(List.of("abc", "def"));
    final var errors = new ArrayList<String>();

    // when
    final var actual = AdvancedSearchFilterUtil.mapToKeyOperations("key", errors).apply(filter);

    // then
    assertThat(errors).hasSize(2);
    assertThat(actual).isEmpty();
  }

  @Test
  void shouldHandleNullElementInListForStringOperations() {
    // given
    final var filter = BasicStringFilter.Builder.builder().build();
    filter.set$In(nullableList("a", null, "b"));

    // when
    final var actual = AdvancedSearchFilterUtil.mapToStringOperations().apply(filter);

    // then — null element causes the whole operation to be dropped
    assertThat(actual).isEmpty();
  }

  @Test
  void shouldHandleNullElementInListForKeyOperations() {
    // given
    final var filter = BasicStringFilter.Builder.builder().build();
    filter.set$In(nullableList("123", null));
    final var errors = new ArrayList<String>();

    // when
    final var actual = AdvancedSearchFilterUtil.mapToKeyOperations("key", errors).apply(filter);

    // then — null element causes the whole operation to be dropped
    assertThat(actual).isEmpty();
  }

  @Test
  void shouldHandleNullElementInListForIntegerOperations() {
    // given
    final var filter = AdvancedIntegerFilter.Builder.builder().build();
    filter.set$In(nullableList(1, null, 2));
    final var errors = new ArrayList<String>();

    // when
    final var actual =
        AdvancedSearchFilterUtil.mapToIntegerOperations("retries", errors).apply(filter);

    // then — null element causes the whole operation to be dropped
    assertThat(actual).isEmpty();
  }

  /** Creates an ArrayList that allows null elements (unlike List.of). */
  @SafeVarargs
  private static <T> List<T> nullableList(final T... elements) {
    final var list = new ArrayList<T>(elements.length);
    java.util.Collections.addAll(list, elements);
    return list;
  }
}
