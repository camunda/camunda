/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.util;

import static org.assertj.core.api.Assertions.*;

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedAdvancedDateTimeFilterStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedAdvancedIntegerFilterStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedAdvancedStringFilterStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedBasicStringFilterStrictContract;
import io.camunda.search.filter.Operation;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;
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

  // --- Record-based filter constructors ---

  private static GeneratedAdvancedIntegerFilterStrictContract intFilter(
      final List<Operation<Integer>> operations) {
    Integer eq = null, neq = null, gt = null, gte = null, lt = null, lte = null;
    Boolean exists = null;
    List<Integer> in = null;
    for (final Operation<Integer> op : operations) {
      switch (op.operator()) {
        case EQUALS -> eq = op.value();
        case NOT_EQUALS -> neq = op.value();
        case EXISTS -> exists = true;
        case NOT_EXISTS -> exists = false;
        case GREATER_THAN -> gt = op.value();
        case GREATER_THAN_EQUALS -> gte = op.value();
        case LOWER_THAN -> lt = op.value();
        case LOWER_THAN_EQUALS -> lte = op.value();
        case IN -> in = op.values();
        default -> throw new IllegalArgumentException("Unsupported: " + op.operator());
      }
    }
    return new GeneratedAdvancedIntegerFilterStrictContract(eq, neq, exists, gt, gte, lt, lte, in);
  }

  private static GeneratedBasicStringFilterStrictContract basicStringFilter(
      final List<Operation<String>> operations) {
    String eq = null, neq = null;
    Boolean exists = null;
    List<String> in = null;
    for (final Operation<String> op : operations) {
      switch (op.operator()) {
        case EQUALS -> eq = op.value();
        case NOT_EQUALS -> neq = op.value();
        case EXISTS -> exists = true;
        case NOT_EXISTS -> exists = false;
        case IN -> in = op.values();
        default -> throw new IllegalArgumentException("Unsupported: " + op.operator());
      }
    }
    return new GeneratedBasicStringFilterStrictContract(eq, neq, exists, in, null);
  }

  private static GeneratedBasicStringFilterStrictContract basicStringFilterFromLong(
      final List<Operation<Long>> operations) {
    return basicStringFilter(
        operations.stream()
            .map(
                op ->
                    new Operation<>(
                        op.operator(),
                        op.values() != null
                            ? op.values().stream().map(String::valueOf).toList()
                            : null))
            .toList());
  }

  private static GeneratedAdvancedStringFilterStrictContract advancedStringFilter(
      final List<Operation<String>> operations) {
    String eq = null, neq = null, like = null;
    Boolean exists = null;
    List<String> in = null;
    for (final Operation<String> op : operations) {
      switch (op.operator()) {
        case EQUALS -> eq = op.value();
        case NOT_EQUALS -> neq = op.value();
        case EXISTS -> exists = true;
        case NOT_EXISTS -> exists = false;
        case IN -> in = op.values();
        case LIKE -> like = op.value();
        default -> throw new IllegalArgumentException("Unsupported: " + op.operator());
      }
    }
    return new GeneratedAdvancedStringFilterStrictContract(eq, neq, exists, in, null, like);
  }

  private static GeneratedAdvancedDateTimeFilterStrictContract dateTimeFilter(
      final List<Operation<OffsetDateTime>> operations) {
    String eq = null, neq = null, gt = null, gte = null, lt = null, lte = null;
    Boolean exists = null;
    List<String> in = null;
    for (final Operation<OffsetDateTime> op : operations) {
      switch (op.operator()) {
        case EQUALS -> eq = op.value().toString();
        case NOT_EQUALS -> neq = op.value().toString();
        case EXISTS -> exists = true;
        case NOT_EXISTS -> exists = false;
        case GREATER_THAN -> gt = op.value().toString();
        case GREATER_THAN_EQUALS -> gte = op.value().toString();
        case LOWER_THAN -> lt = op.value().toString();
        case LOWER_THAN_EQUALS -> lte = op.value().toString();
        case IN -> in = op.values().stream().map(OffsetDateTime::toString).toList();
        default -> throw new IllegalArgumentException("Unsupported: " + op.operator());
      }
    }
    return new GeneratedAdvancedDateTimeFilterStrictContract(eq, neq, exists, gt, gte, lt, lte, in);
  }

  private static Stream<Arguments> provideAdvancedFilterParameters() {
    final var streamBuilder = Stream.<Arguments>builder();
    // AdvancedIntegerFilter
    BASIC_LONG_OPERATIONS.stream()
        .map(ops -> ops.stream().map(AdvancedSearchFilterUtilTest::toIntOperation).toList())
        .map(ops -> Arguments.of(intFilter(ops), Integer.class, ops))
        .forEach(streamBuilder::add);
    LONG_OPERATIONS.stream()
        .map(ops -> ops.stream().map(AdvancedSearchFilterUtilTest::toIntOperation).toList())
        .map(ops -> Arguments.of(intFilter(ops), Integer.class, ops))
        .forEach(streamBuilder::add);
    // BasicStringFilter
    BASIC_STRING_OPERATIONS.stream()
        .map(ops -> Arguments.of(basicStringFilter(ops), String.class, ops))
        .forEach(streamBuilder::add);
    // BasicStringFilter - String keys to long
    BASIC_LONG_OPERATIONS.stream()
        .map(ops -> Arguments.of(basicStringFilterFromLong(ops), Long.class, ops))
        .forEach(streamBuilder::add);
    // AdvancedStringFilter
    BASIC_STRING_OPERATIONS.stream()
        .map(ops -> Arguments.of(advancedStringFilter(ops), String.class, ops))
        .forEach(streamBuilder::add);
    STRING_OPERATIONS.stream()
        .map(ops -> Arguments.of(advancedStringFilter(ops), String.class, ops))
        .forEach(streamBuilder::add);
    // AdvancedDateTimeFilter
    DATE_TIME_OPERATIONS.stream()
        .map(ops -> Arguments.of(dateTimeFilter(ops), OffsetDateTime.class, ops))
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
      final Object filter, final Class<T> mappedClass, final List<Operation<T>> operations) {
    // when
    final var actual = AdvancedSearchFilterUtil.mapToOperations(filter, mappedClass);
    // then
    assertThat(actual).hasSize(operations.size());
    assertThat(actual).containsExactlyInAnyOrderElementsOf(operations);
  }

  @Test
  public void shouldMapToStringOperations() {
    // given
    final var filter =
        new GeneratedAdvancedIntegerFilterStrictContract(
            10, null, null, null, null, null, null, null);
    // when
    final var actual = AdvancedSearchFilterUtil.mapToOperations(filter, String.class);
    // then
    assertThat(actual).hasSize(1);
    assertThat(actual.getFirst()).isEqualTo(Operation.eq("10"));
  }

  @Test
  public void shouldMapToLongOperations() {
    // given
    final var filter = new GeneratedBasicStringFilterStrictContract("10", null, null, null, null);
    // when
    final var actual = AdvancedSearchFilterUtil.mapToOperations(filter, Long.class);
    // then
    assertThat(actual).hasSize(1);
    assertThat(actual.getFirst()).isEqualTo(Operation.eq(10L));
  }

  @Test
  void shouldThrowExceptionWhenCannotConvert() {
    // given
    final var filter =
        new GeneratedAdvancedIntegerFilterStrictContract(
            10, null, null, null, null, null, null, null);

    // when/then
    assertThatThrownBy(() -> AdvancedSearchFilterUtil.mapToOperations(filter, Boolean.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Could not convert request value [10] to [java.lang.Boolean]");
  }

  @Test
  void shouldThrowExceptionWhenDateInvalid() {
    // given
    final var filter =
        new GeneratedAdvancedDateTimeFilterStrictContract(
            "2023-11-11T10:10:10.1010+0100", null, null, null, null, null, null, null);

    // when/then
    assertThatThrownBy(() -> AdvancedSearchFilterUtil.mapToOperations(filter, OffsetDateTime.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Failed to parse date-time: [2023-11-11T10:10:10.1010+0100]");
  }

  @Test
  void shouldThrowExceptionWhenLongValueInvalid() {
    // given
    final var filter = new GeneratedBasicStringFilterStrictContract("meow", null, null, null, null);

    // when/then
    assertThatThrownBy(() -> AdvancedSearchFilterUtil.mapToOperations(filter, Long.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("The provided value 'meow' is not a valid key.");
  }
}
