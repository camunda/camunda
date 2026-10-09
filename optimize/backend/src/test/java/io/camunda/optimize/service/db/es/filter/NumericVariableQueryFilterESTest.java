/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.filter;

import static io.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.GREATER_THAN;
import static io.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.GREATER_THAN_EQUALS;
import static io.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.LESS_THAN;
import static io.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.LESS_THAN_EQUALS;
import static io.camunda.optimize.service.db.schema.index.AbstractInstanceIndex.MULTIVALUE_FIELD_DOUBLE;
import static io.camunda.optimize.service.db.schema.index.AbstractInstanceIndex.MULTIVALUE_FIELD_LONG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import co.elastic.clients.elasticsearch._types.query_dsl.LongNumberRangeQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.OperatorMultipleValuesFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.OperatorMultipleValuesVariableFilterDataDto;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import java.util.Collections;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

public class NumericVariableQueryFilterESTest {

  private final ProcessVariableQueryFilterES processVariableFilter =
      new ProcessVariableQueryFilterES();
  private final DecisionInputVariableQueryFilterES decisionVariableFilter =
      new DecisionInputVariableQueryFilterES();

  /**
   * Relative operators combined with values that bracket the {@code double} precision boundary.
   * Beyond 2^53 not every {@code long} has an exact {@code double} representation, and the gap
   * between representable values doubles every power of two (2 between 2^53-2^54, 4 up to 2^55, 16
   * in the ~2^56 band, ...). Long process/decision variables holding such values — snowflake IDs,
   * epoch-nanosecond timestamps, large counters — are rounded off by a lossy {@code double}
   * conversion, so the filter silently matches the wrong instances (see #58074).
   *
   * <p>Each argument carries whether the value genuinely loses precision as a {@code double}, so
   * the tests can self-check that the "large" values actually exercise the rounding.
   */
  private static Stream<Arguments> relativeOperatorsAndBoundaryLongValues() {
    return Stream.of(LESS_THAN, LESS_THAN_EQUALS, GREATER_THAN, GREATER_THAN_EQUALS)
        .flatMap(
            operator ->
                Stream.of(
                    arguments(operator, 123L, false), // below 2^53, exactly representable — control
                    arguments(operator, (1L << 53) + 1, true), // just above 2^53, gap 2 — rounds
                    arguments(operator, 75309396599875985L, true), // ~2^56 band — rounds DOWN (-1)
                    arguments(operator, 75309396599875993L, true))); // ~2^56 band — rounds UP (+7)
  }

  @ParameterizedTest(name = "{0} {1}")
  @MethodSource("relativeOperatorsAndBoundaryLongValues")
  public void shouldBuildProcessVariableRangeQueryWithExactLongBounds(
      final FilterOperator operator, final long value, final boolean losesPrecisionAsDouble) {
    // given
    final OperatorMultipleValuesVariableFilterDataDto filter =
        numericFilter(VariableType.LONG, operator, value);

    // when
    final RangeQuery range =
        extractRangeQuery(processVariableFilter.createNumericQueryBuilder(filter).build());

    // then the exact long bound must reach Elasticsearch. The variable value is indexed in a
    // long-mapped sub-field, so a bound routed through double rounds beyond 2^53 and the report
    // wrongly includes or excludes instances around it.
    assertThat(range.isLongNumber()).isTrue();
    assertThat(range.longNumber().field()).endsWith("." + MULTIVALUE_FIELD_LONG);
    assertThat(boundFor(range.longNumber(), operator)).isEqualTo(value);

    assertValueGenuinelyExercisesRounding(value, losesPrecisionAsDouble);
  }

  @ParameterizedTest(name = "{0} {1}")
  @MethodSource("relativeOperatorsAndBoundaryLongValues")
  public void shouldBuildDecisionVariableRangeQueryWithExactLongBounds(
      final FilterOperator operator, final long value, final boolean losesPrecisionAsDouble) {
    // given
    final OperatorMultipleValuesVariableFilterDataDto filter =
        numericFilter(VariableType.LONG, operator, value);

    // when
    final RangeQuery range =
        extractRangeQuery(decisionVariableFilter.createNumericQueryBuilder(filter).build());

    // then (see the process variable test for why a lossy double bound corrupts the filter)
    assertThat(range.isLongNumber()).isTrue();
    assertThat(range.longNumber().field()).endsWith("." + MULTIVALUE_FIELD_LONG);
    assertThat(boundFor(range.longNumber(), operator)).isEqualTo(value);

    assertValueGenuinelyExercisesRounding(value, losesPrecisionAsDouble);
  }

  @ParameterizedTest
  @EnumSource(
      value = VariableType.class,
      names = {"SHORT", "INTEGER", "LONG"})
  public void shouldUseExactLongBoundsForAllIntegralVariableTypes(final VariableType type) {
    // given a value within the range of the given type
    final OperatorMultipleValuesVariableFilterDataDto filter =
        numericFilter(type, GREATER_THAN, 123L);

    // when
    final RangeQuery range =
        extractRangeQuery(processVariableFilter.createNumericQueryBuilder(filter).build());

    // then all integral types share the long-mapped value sub-field, so all of them must use the
    // exact long range variant
    assertThat(range.isLongNumber()).isTrue();
    assertThat(range.longNumber().gt()).isEqualTo(123L);
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(
      value = FilterOperator.class,
      names = {"LESS_THAN", "LESS_THAN_EQUALS", "GREATER_THAN", "GREATER_THAN_EQUALS"})
  public void shouldBuildDoubleVariableRangeQueryWithDoubleBounds(final FilterOperator operator) {
    // given
    final OperatorMultipleValuesVariableFilterDataDto filter =
        new OperatorMultipleValuesVariableFilterDataDto(
            "aVariableName",
            VariableType.DOUBLE,
            new OperatorMultipleValuesFilterDataDto(operator, Collections.singletonList("12.5")));

    // when
    final RangeQuery range =
        extractRangeQuery(processVariableFilter.createNumericQueryBuilder(filter).build());

    // then double variables keep the double-mapped field and bound
    assertThat(range.isNumber()).isTrue();
    assertThat(range.number().field()).endsWith("." + MULTIVALUE_FIELD_DOUBLE);
    assertThat(
            switch (operator) {
              case LESS_THAN -> range.number().lt();
              case LESS_THAN_EQUALS -> range.number().lte();
              case GREATER_THAN -> range.number().gt();
              default -> range.number().gte();
            })
        .isEqualTo(12.5);
  }

  private static OperatorMultipleValuesVariableFilterDataDto numericFilter(
      final VariableType type, final FilterOperator operator, final long value) {
    return new OperatorMultipleValuesVariableFilterDataDto(
        "aVariableName",
        type,
        new OperatorMultipleValuesFilterDataDto(
            operator, Collections.singletonList(String.valueOf(value))));
  }

  private static RangeQuery extractRangeQuery(final Query filterQuery) {
    return filterQuery.nested().query().bool().must().stream()
        .filter(Query::isRange)
        .map(Query::range)
        .findFirst()
        .orElseThrow();
  }

  private static Long boundFor(final LongNumberRangeQuery range, final FilterOperator operator) {
    return switch (operator) {
      case LESS_THAN -> range.lt();
      case LESS_THAN_EQUALS -> range.lte();
      case GREATER_THAN -> range.gt();
      case GREATER_THAN_EQUALS -> range.gte();
      default -> throw new IllegalArgumentException("Not a relative operator: " + operator);
    };
  }

  // Guards the value source against silently rotting: if a "large" value were ever swapped for one
  // that is exactly representable as a double, the bound assertions above would pass even against
  // the lossy implementation and stop protecting against the regression.
  private static void assertValueGenuinelyExercisesRounding(
      final long value, final boolean losesPrecisionAsDouble) {
    if (losesPrecisionAsDouble) {
      assertThat((long) (double) value).isNotEqualTo(value);
    } else {
      assertThat((long) (double) value).isEqualTo(value);
    }
  }
}
