/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.util;

import static org.assertj.core.api.Assertions.*;

import io.camunda.gateway.protocol.model.AdvancedDateTimeFilter;
import io.camunda.gateway.protocol.model.AdvancedIntegerFilter;
import io.camunda.gateway.protocol.model.AdvancedStringFilter;
import io.camunda.gateway.protocol.model.BasicStringFilter;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.Operator;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AdvancedSearchFilterUtilTest {

  private static final List<List<Operation<Long>>> LONG_OPERATIONS =
      List.of(
          List.of(Operation.gt(5L)),
          List.of(Operation.gte(5L)),
          List.of(Operation.lt(5L)),
          List.of(Operation.lte(5L)),
          List.of(Operation.gt(5L), Operation.lt(10L)),
          List.of(Operation.gte(5L), Operation.lte(10L)));
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

  private <F, T> F constructFilter(
      final Class<F> fClass, final Class<T> pClass, final List<Operation<T>> operations)
      throws Exception {
    final var filter = fClass.getDeclaredConstructor().newInstance();
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
        case IN ->
            method.invoke(
                filter,
                op.values().stream()
                    .map(v -> AdvancedSearchFilterUtil.convertValue(pClass, v))
                    .toList());
        case EXISTS, NOT_EXISTS -> method.invoke(filter, operator.equals(Operator.EXISTS));
        default -> method.invoke(filter, AdvancedSearchFilterUtil.convertValue(pClass, op.value()));
      }
    }
    return filter;
  }

  private static Stream<Arguments> provideAdvancedFilterParameters() {
    final var streamBuilder = Stream.<Arguments>builder();
    // AdvancedIntegerFilter
    BASIC_LONG_OPERATIONS.stream()
        .map(ops -> ops.stream().map(AdvancedSearchFilterUtilTest::toIntOperation).toList())
        .map(ops -> Arguments.of(AdvancedIntegerFilter.class, Integer.class, Integer.class, ops))
        .forEach(streamBuilder::add);
    LONG_OPERATIONS.stream()
        .map(ops -> ops.stream().map(AdvancedSearchFilterUtilTest::toIntOperation).toList())
        .map(ops -> Arguments.of(AdvancedIntegerFilter.class, Integer.class, Integer.class, ops))
        .forEach(streamBuilder::add);
    // BasicStringFilter
    BASIC_STRING_OPERATIONS.stream()
        .map(ops -> Arguments.of(BasicStringFilter.class, String.class, String.class, ops))
        .forEach(streamBuilder::add);
    // BasicStringFilter - String keys to long
    BASIC_LONG_OPERATIONS.stream()
        .map(ops -> Arguments.of(BasicStringFilter.class, String.class, Long.class, ops))
        .forEach(streamBuilder::add);
    // AdvancedStringFilter
    BASIC_STRING_OPERATIONS.stream()
        .map(ops -> Arguments.of(AdvancedStringFilter.class, String.class, String.class, ops))
        .forEach(streamBuilder::add);
    STRING_OPERATIONS.stream()
        .map(ops -> Arguments.of(AdvancedStringFilter.class, String.class, String.class, ops))
        .forEach(streamBuilder::add);
    // AdvancedDateTimeFilter
    DATE_TIME_OPERATIONS.stream()
        .map(
            ops ->
                Arguments.of(AdvancedDateTimeFilter.class, String.class, OffsetDateTime.class, ops))
        .forEach(streamBuilder::add);
    return streamBuilder.build();
  }

  private static Operation<Integer> toIntOperation(final Operation<Long> op) {
    return new Operation<>(
        op.operator(),
        op.values() != null ? op.values().stream().map(Long::intValue).toList() : null);
  }

  @ParameterizedTest
  @MethodSource("provideAdvancedFilterParameters")
  public <T> void shouldMapAdvancedFilterCorrectly(
      final Class<?> filterClass,
      final Class<T> filterValueClass,
      final Class<T> mappedClass,
      final List<Operation<T>> operations)
      throws Exception {
    // given
    final var filter = constructFilter(filterClass, filterValueClass, operations);
    // when
    final var actual = AdvancedSearchFilterUtil.mapToOperations(filter, mappedClass);
    // then
    assertThat(actual).hasSize(operations.size());
    assertThat(actual).containsExactlyInAnyOrderElementsOf(operations);
  }

  @Test
  public void shouldMapToStringOperations() {
    // given
    final var filter = new AdvancedIntegerFilter();
    filter.set$Eq(10);
    // when
    final var actual = AdvancedSearchFilterUtil.mapToOperations(filter, String.class);
    // then
    assertThat(actual).hasSize(1);
    assertThat(actual.getFirst()).isEqualTo(Operation.eq("10"));
  }

  @Test
  public void shouldMapToLongOperations() {
    // given
    final var filter = new BasicStringFilter();
    filter.set$Eq("10");
    // when
    final var actual = AdvancedSearchFilterUtil.mapToOperations(filter, Long.class);
    // then
    assertThat(actual).hasSize(1);
    assertThat(actual.getFirst()).isEqualTo(Operation.eq(10L));
  }

  @Test
  void shouldThrowExceptionWhenCannotConvert() {
    // given
    final var filter = new AdvancedIntegerFilter();
    filter.set$Eq(10);

    // when/then
    assertThatThrownBy(() -> AdvancedSearchFilterUtil.mapToOperations(filter, Boolean.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Could not convert request value [10] to [java.lang.Boolean]");
  }

  @Test
  void shouldThrowExceptionWhenDateInvalid() {
    // given
    final var filter = new AdvancedDateTimeFilter();
    filter.set$Eq("2023-11-11T10:10:10.1010+0100");

    // when/then
    assertThatThrownBy(() -> AdvancedSearchFilterUtil.mapToOperations(filter, OffsetDateTime.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Failed to parse date-time: [2023-11-11T10:10:10.1010+0100]");
  }
}
