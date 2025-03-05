/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest;

import io.camunda.search.filter.Operation;
import io.camunda.search.filter.Operator;
import io.camunda.security.auth.Authentication;
import io.camunda.zeebe.gateway.rest.config.JacksonConfig;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;

@TestPropertySource(
    properties = {
      "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration"
    })
@Import(JacksonConfig.class)
public abstract class RestControllerTest {
  public static final List<List<Operation<Long>>> LONG_OPERATIONS =
      List.of(
          List.of(Operation.gt(5L)),
          List.of(Operation.gte(5L)),
          List.of(Operation.lt(5L)),
          List.of(Operation.lte(5L)),
          List.of(Operation.gt(5L), Operation.lt(10L)),
          List.of(Operation.gte(5L), Operation.lte(10L)));
  public static final List<List<Operation<Long>>> BASIC_LONG_OPERATIONS =
      List.of(
          List.of(Operation.eq(10L)),
          List.of(Operation.neq(1L)),
          List.of(Operation.exists(true)),
          List.of(Operation.exists(false)),
          List.of(Operation.in(5L, 10L)));
  public static final List<List<Operation<String>>> BASIC_STRING_OPERATIONS =
      List.of(
          List.of(Operation.eq("this")),
          List.of(Operation.neq("that")),
          List.of(Operation.exists(true)),
          List.of(Operation.exists(false)),
          List.of(Operation.in("this", "that")));
  public static final List<List<Operation<String>>> STRING_OPERATIONS =
      List.of(
          List.of(Operation.like("th%")),
          List.of(Operation.in("this", "that"), Operation.like("th%")));
  public static final List<List<Operation<OffsetDateTime>>> DATE_TIME_OPERATIONS =
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
  @Autowired protected WebTestClient webClient;

  public ResponseSpec withMultiTenancy(
      final String tenantId, final Function<WebTestClient, ResponseSpec> function) {
    try (final MockedStatic<RequestMapper> mockRequestMapper =
        Mockito.mockStatic(RequestMapper.class, Mockito.CALLS_REAL_METHODS)) {
      mockRequestMapper
          .when(RequestMapper::getAuthentication)
          .thenReturn(Authentication.of(a -> a.user("foo").group(456L).tenant(tenantId)));
      mockRequestMapper.when(RequestMapper::getAuthorizedTenants).thenReturn(Set.of(tenantId));
      return function.apply(webClient);
    }
  }

  protected static <T> Arguments generateParameterizedArguments(
      final String filterKey,
      final Function<List<Operation<T>>, Object> consumer,
      final List<Operation<T>> operations,
      final boolean stringValues) {
    return Arguments.of(
        operationsToJSON(filterKey, operations, stringValues), consumer.apply(operations));
  }

  public static Operation<Integer> toIntOperation(final Operation<Long> op) {
    return new Operation<>(
        op.operator(),
        op.values() != null ? op.values().stream().map(Long::intValue).toList() : null);
  }

  public static void integerOperationTestCases(
      final Stream.Builder<Arguments> streamBuilder,
      final String filterKey,
      final Function<List<Operation<Integer>>, Object> builderMethod) {
    BASIC_LONG_OPERATIONS.stream()
        .map(ops -> ops.stream().map(RestControllerTest::toIntOperation).toList())
        .map(ops -> generateParameterizedArguments(filterKey, builderMethod, ops, false))
        .forEach(streamBuilder::add);
    LONG_OPERATIONS.stream()
        .map(ops -> ops.stream().map(RestControllerTest::toIntOperation).toList())
        .map(ops -> generateParameterizedArguments(filterKey, builderMethod, ops, false))
        .forEach(streamBuilder::add);
  }

  public static void keyOperationTestCases(
      final Stream.Builder<Arguments> streamBuilder,
      final String filterKey,
      final Function<List<Operation<Long>>, Object> builderMethod) {
    BASIC_LONG_OPERATIONS.stream()
        .map(ops -> generateParameterizedArguments(filterKey, builderMethod, ops, true))
        .forEach(streamBuilder::add);
  }

  public static void stringOperationTestCases(
      final Stream.Builder<Arguments> streamBuilder,
      final String filterKey,
      final Function<List<Operation<String>>, Object> builderMethod) {
    BASIC_STRING_OPERATIONS.stream()
        .map(ops -> generateParameterizedArguments(filterKey, builderMethod, ops, true))
        .forEach(streamBuilder::add);
    STRING_OPERATIONS.stream()
        .map(ops -> generateParameterizedArguments(filterKey, builderMethod, ops, true))
        .forEach(streamBuilder::add);
  }

  public static void dateTimeOperationTestCases(
      final Stream.Builder<Arguments> streamBuilder,
      final String filterKey,
      final Function<List<Operation<OffsetDateTime>>, Object> builderMethod) {
    DATE_TIME_OPERATIONS.stream()
        .map(ops -> generateParameterizedArguments(filterKey, builderMethod, ops, true))
        .forEach(streamBuilder::add);
  }

  public static <T> void customOperationTestCases(
      final Stream.Builder<Arguments> streamBuilder,
      final String filterKey,
      final Function<List<Operation<T>>, Object> builderMethod,
      final List<List<Operation<T>>> operations,
      final boolean stringValues) {
    operations.stream()
        .map(ops -> generateParameterizedArguments(filterKey, builderMethod, ops, stringValues))
        .forEach(streamBuilder::add);
  }

  public static <T> String operationsToJSON(
      final String filterKey, final List<Operation<T>> operations, final boolean stringValues) {

    final var implicitTpl = stringValues ? "\"%s\"" : "%s";
    final var keyValueTpl = "\"%s\": %s";
    final var explicitTpl = "\"%s\": " + (stringValues ? "\"%s\"" : "%s");
    final var filterValue =
        operations.stream()
            .map(
                op ->
                    switch (op.operator()) {
                      case EQUALS -> implicitTpl.formatted(op.value());
                      case NOT_EQUALS -> explicitTpl.formatted("$neq", op.value());
                      case EXISTS -> explicitTpl.formatted("$exists", true);
                      case NOT_EXISTS -> explicitTpl.formatted("$exists", false);
                      case GREATER_THAN -> explicitTpl.formatted("$gt", op.value());
                      case GREATER_THAN_EQUALS -> explicitTpl.formatted("$gte", op.value());
                      case LOWER_THAN -> explicitTpl.formatted("$lt", op.value());
                      case LOWER_THAN_EQUALS -> explicitTpl.formatted("$lte", op.value());
                      case IN ->
                          keyValueTpl.formatted(
                              "$in",
                              stringValues
                                  ? op.values().stream().map(implicitTpl::formatted).toList()
                                  : op.values());
                      case LIKE -> explicitTpl.formatted("$like", op.value());
                    })
            .collect(Collectors.joining(","));
    if (operations.size() == 1 && operations.getFirst().operator().equals(Operator.EQUALS)) {
      // implicit case
      return "{%s}".formatted(keyValueTpl).formatted(filterKey, filterValue);
    }
    return "{\"%s\": {%s}}".formatted(filterKey, filterValue);
  }
}
