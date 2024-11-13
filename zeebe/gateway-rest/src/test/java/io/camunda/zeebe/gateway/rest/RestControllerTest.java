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
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;

@TestPropertySource(
    properties = {
      "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration"
    })
public abstract class RestControllerTest {
  protected static final List<List<Operation<Long>>> BASIC_LONG_OPERATIONS =
      List.of(
          List.of(Operation.eq(10L)),
          List.of(Operation.neq(1L)),
          List.of(Operation.exists(true)),
          List.of(Operation.exists(false)),
          List.of(Operation.in(5L, 10L)));
  protected static final List<List<Operation<Long>>> LONG_OPERATIONS =
      List.of(
          List.of(Operation.gt(5L)),
          List.of(Operation.gte(5L)),
          List.of(Operation.lt(5L)),
          List.of(Operation.lte(5L)),
          List.of(Operation.gt(5L), Operation.lt(10L)),
          List.of(Operation.gte(5L), Operation.lte(10L)));
  protected static final List<List<Operation<Integer>>> INTEGER_OPERATIONS =
      List.of(
          List.of(Operation.eq(10)),
          List.of(Operation.neq(1)),
          List.of(Operation.exists(true)),
          List.of(Operation.exists(false)),
          List.of(Operation.gt(5)),
          List.of(Operation.gte(5)),
          List.of(Operation.lt(5)),
          List.of(Operation.lte(5)),
          List.of(Operation.in(5, 10)),
          List.of(Operation.gt(5), Operation.lt(10)),
          List.of(Operation.gte(5), Operation.lte(10)));
  @Autowired protected WebTestClient webClient;

  public ResponseSpec withMultiTenancy(
      final String tenantId, final Function<WebTestClient, ResponseSpec> function) {
    try (final MockedStatic<RequestMapper> mockRequestMapper =
        Mockito.mockStatic(RequestMapper.class, Mockito.CALLS_REAL_METHODS)) {
      mockRequestMapper
          .when(RequestMapper::getAuthentication)
          .thenReturn(
              Authentication.of(a -> a.user(123L).group(456L).tenant(tenantId).token("token")));
      return function.apply(webClient);
    }
  }

  protected static <T> Arguments generateParameterizedArguments(
      final String filterKey,
      final Function<List<Operation<T>>, Object> consumer,
      final List<Operation<T>> operations) {
    return Arguments.of(operationsToJSON(filterKey, operations), consumer.apply(operations));
  }

  public static void basicLongOperationTestCases(
      final Stream.Builder<Arguments> streamBuilder,
      final String filterKey,
      final Function<List<Operation<Long>>, Object> builderMethod) {
    BASIC_LONG_OPERATIONS.stream()
        .map(ops -> generateParameterizedArguments(filterKey, builderMethod, ops))
        .forEach(streamBuilder::add);
  }

  public static void longOperationTestCases(
      final Stream.Builder<Arguments> streamBuilder,
      final String filterKey,
      final Function<List<Operation<Long>>, Object> builderMethod) {
    basicLongOperationTestCases(streamBuilder, filterKey, builderMethod);
    LONG_OPERATIONS.stream()
        .map(ops -> generateParameterizedArguments(filterKey, builderMethod, ops))
        .forEach(streamBuilder::add);
  }

  public static void integerOperationTestCases(
      final Stream.Builder<Arguments> streamBuilder,
      final String filterKey,
      final Function<List<Operation<Integer>>, Object> builderMethod) {
    INTEGER_OPERATIONS.stream()
        .map(ops -> generateParameterizedArguments(filterKey, builderMethod, ops))
        .forEach(streamBuilder::add);
  }

  public static <T> String operationsToJSON(
      final String filterKey, final List<Operation<T>> operations) {

    final var filterTemplate = "\"%s\": %s";
    final var filterValue =
        operations.stream()
            .map(
                op ->
                    switch (op.operator()) {
                      case EQUALS -> "%s".formatted(op.value());
                      case NOT_EQUALS -> filterTemplate.formatted("$neq", op.value());
                      case EXISTS -> filterTemplate.formatted("$exists", true);
                      case NOT_EXISTS -> filterTemplate.formatted("$exists", false);
                      case GREATER_THAN -> filterTemplate.formatted("$gt", op.value());
                      case GREATER_THAN_EQUALS -> filterTemplate.formatted("$gte", op.value());
                      case LOWER_THAN -> filterTemplate.formatted("$lt", op.value());
                      case LOWER_THAN_EQUALS -> filterTemplate.formatted("$lte", op.value());
                      case IN -> filterTemplate.formatted("$in", op.values());
                      case LIKE -> filterTemplate.formatted("$like", op.value());
                    })
            .collect(Collectors.joining(","));
    if (operations.size() == 1 && operations.getFirst().operator().equals(Operator.EQUALS)) {
      // implicit case
      return "{\"%s\": %s}".formatted(filterKey, filterValue);
    }
    return "{\"%s\": {%s}}".formatted(filterKey, filterValue);
  }
}
