/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.search.filter.Operation;
import io.camunda.search.filter.Operator;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.validation.IdentifierValidator;
import io.camunda.zeebe.gateway.rest.config.JacksonConfig;
import io.camunda.zeebe.gateway.rest.interceptor.SecondaryStorageInterceptor;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

@TestPropertySource(
    properties = {
      "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration"
    })
@Import({JacksonConfig.class, RestControllerTest.WebMvcTestConfig.class})
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
  protected static final CamundaAuthentication AUTHENTICATION_WITH_DEFAULT_TENANT =
      CamundaAuthentication.of(a -> a.user("foo").group("groupId").tenant("<default>"));
  protected static final CamundaAuthentication AUTHENTICATION_WITH_NON_DEFAULT_TENANT =
      CamundaAuthentication.of(a -> a.user("foo").group("groupId").tenant("tenantId"));
  private static final Pattern ID_PATTERN = Pattern.compile(SecurityConfiguration.DEFAULT_ID_REGEX);
  @Autowired protected WebTestClient webClient;
  @MockitoBean protected SecondaryStorageInterceptor secondaryStorageInterceptor;

  @BeforeEach
  void setup() {
    when(secondaryStorageInterceptor.preHandle(any(), any(), any())).thenReturn(true);
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

  public static void basicStringOperationTestCases(
      final Stream.Builder<Arguments> streamBuilder,
      final String filterKey,
      final Function<List<Operation<String>>, Object> builderMethod) {
    BASIC_STRING_OPERATIONS.stream()
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
    final var booleanTpl = "\"%s\": %s";
    final var explicitTpl = "\"%s\": " + (stringValues ? "\"%s\"" : "%s");
    final var filterValue =
        operations.stream()
            .map(
                op ->
                    switch (op.operator()) {
                      case EQUALS -> implicitTpl.formatted(op.value());
                      case NOT_EQUALS -> explicitTpl.formatted("$neq", op.value());
                      case EXISTS -> booleanTpl.formatted("$exists", true);
                      case NOT_EXISTS -> booleanTpl.formatted("$exists", false);
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
                      case NOT_IN ->
                          keyValueTpl.formatted(
                              "$notIn",
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

  @TestConfiguration
  public static class WebMvcTestConfig {
    @Bean
    public IdentifierValidator identifierValidator() {
      return new IdentifierValidator(ID_PATTERN, ID_PATTERN);
    }
  }
}
