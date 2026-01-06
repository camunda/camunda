/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.util;

import static org.assertj.core.api.Assertions.*;

import io.camunda.gateway.protocol.model.AdvancedDateTimeFilter;
import io.camunda.gateway.protocol.model.AdvancedIntegerFilter;
import io.camunda.gateway.protocol.model.AdvancedStringFilter;
import io.camunda.gateway.protocol.model.BasicStringFilter;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.Operator;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AdvancedSearchFilterUtilTest {

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
    RestControllerTest.BASIC_LONG_OPERATIONS.stream()
        .map(ops -> ops.stream().map(RestControllerTest::toIntOperation).toList())
        .map(ops -> Arguments.of(AdvancedIntegerFilter.class, Integer.class, Integer.class, ops))
        .forEach(streamBuilder::add);
    RestControllerTest.LONG_OPERATIONS.stream()
        .map(ops -> ops.stream().map(RestControllerTest::toIntOperation).toList())
        .map(ops -> Arguments.of(AdvancedIntegerFilter.class, Integer.class, Integer.class, ops))
        .forEach(streamBuilder::add);
    // BasicStringFilter
    RestControllerTest.BASIC_STRING_OPERATIONS.stream()
        .map(ops -> Arguments.of(BasicStringFilter.class, String.class, String.class, ops))
        .forEach(streamBuilder::add);
    // BasicStringFilter - String keys to long
    RestControllerTest.BASIC_LONG_OPERATIONS.stream()
        .map(ops -> Arguments.of(BasicStringFilter.class, String.class, Long.class, ops))
        .forEach(streamBuilder::add);
    // AdvancedStringFilter
    RestControllerTest.BASIC_STRING_OPERATIONS.stream()
        .map(ops -> Arguments.of(AdvancedStringFilter.class, String.class, String.class, ops))
        .forEach(streamBuilder::add);
    RestControllerTest.STRING_OPERATIONS.stream()
        .map(ops -> Arguments.of(AdvancedStringFilter.class, String.class, String.class, ops))
        .forEach(streamBuilder::add);
    // AdvancedDateTimeFilter
    RestControllerTest.DATE_TIME_OPERATIONS.stream()
        .map(
            ops ->
                Arguments.of(AdvancedDateTimeFilter.class, String.class, OffsetDateTime.class, ops))
        .forEach(streamBuilder::add);
    return streamBuilder.build();
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
