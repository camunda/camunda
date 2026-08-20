/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.filter;

import static org.assertj.core.api.Assertions.*;

import io.camunda.search.entities.ValueTypeEnum;
import java.util.List;
import org.junit.jupiter.api.Test;

class UntypedOperationTest {

  @Test
  void shouldCreateUntypedOperation() {
    final Operation<String> operation = new Operation<>(Operator.EQUALS, List.of("value"));
    final UntypedOperation untypedOperation = UntypedOperation.of(operation);

    assertThat(untypedOperation).isNotNull();
    assertThat(untypedOperation.operator()).isEqualTo(Operator.EQUALS);
    assertThat(untypedOperation.type()).isEqualTo(ValueTypeEnum.STRING);
    assertThat(untypedOperation.value()).isEqualTo("value");
  }

  @Test
  void shouldThrowExceptionForInvalidValueType() {
    final Operation<String> operation = new Operation<>(Operator.GREATER_THAN, List.of("value"));
    final IllegalArgumentException exception =
        catchThrowableOfType(IllegalArgumentException.class, () -> UntypedOperation.of(operation));

    assertThat(exception)
        .hasMessage("Unsupported value type for number operator (<,<=,>,>=): STRING");
  }

  @Test
  void shouldCreateUntypedOperationWithLikeOperator() {
    final Operation<String> operation = new Operation<>(Operator.LIKE, List.of("value"));
    final UntypedOperation untypedOperation = UntypedOperation.of(operation);

    assertThat(untypedOperation).isNotNull();
    assertThat(untypedOperation.operator()).isEqualTo(Operator.LIKE);
    assertThat(untypedOperation.type()).isEqualTo(ValueTypeEnum.STRING);
    assertThat(untypedOperation.value()).isEqualTo("value");
  }

  @Test
  void shouldThrowExceptionForInvalidValueTypeWithLikeOperator() {
    final Operation<Integer> operation = new Operation<>(Operator.LIKE, List.of(123));
    final IllegalArgumentException exception =
        catchThrowableOfType(IllegalArgumentException.class, () -> UntypedOperation.of(operation));

    assertThat(exception).hasMessage("Unsupported value type for like operator: LONG");
  }
}
