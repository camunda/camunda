/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.util.asserts;

import static org.assertj.core.api.Assertions.assertThatCode;

import io.camunda.zeebe.util.Either;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@Execution(ExecutionMode.CONCURRENT)
final class EitherAssertTest {
  @Nested
  final class RightTest {
    private final Either<Object, Object> actual = Either.right("right");
    private final EitherAssert<Object, Object> assertion = EitherAssert.assertThat(actual);

    @Test
    void shouldFailIsLeftOnRight() {
      assertThatCode(assertion::isLeft).isInstanceOf(AssertionError.class);
    }

    @Test
    void shouldFailLeftOnRight() {
      assertThatCode(assertion::left).isInstanceOf(AssertionError.class);
    }

    @Test
    void isRightOnRight() {
      assertion.isRight();
    }

    @Test
    void shouldExtractRightOnRight() {
      assertion.right().isEqualTo("right");
    }
  }

  @Nested
  final class LeftTest {
    private final Either<Object, Object> actual = Either.left("left");
    private final EitherAssert<Object, Object> assertion = EitherAssert.assertThat(actual);

    @Test
    void shouldFailIsRightOnLeft() {
      assertThatCode(assertion::isRight).isInstanceOf(AssertionError.class);
    }

    @Test
    void shouldFailRightOnLeft() {
      assertThatCode(assertion::right).isInstanceOf(AssertionError.class);
    }

    @Test
    void isLeftOnLeft() {
      assertion.isLeft();
    }

    @Test
    void shouldExtractLeftOnLeft() {
      assertion.left().isEqualTo("left");
    }
  }

  @Nested
  final class FailOnNullTest {
    private final EitherAssert<Object, Object> eitherAssert = EitherAssert.assertThat(null);

    @ParameterizedTest(name = "{0}")
    @MethodSource("assertions")
    void shouldFailOnNull(
        @SuppressWarnings("unused") final String testName,
        final Consumer<EitherAssert<Object, Object>> assertion) {
      assertThatCode(() -> assertion.accept(eitherAssert)).isInstanceOf(AssertionError.class);
    }

    private static Stream<Arguments> assertions() {
      return Stream.of(
          of("isLeft", EitherAssert::isLeft),
          of("isRight", EitherAssert::isRight),
          of("left", EitherAssert::left),
          of("right", EitherAssert::right));
    }

    /** Convenience method to infer the type of the consumer in the test cases above. */
    private static Arguments of(
        final String testName, final Consumer<EitherAssert<Object, Object>> assertion) {
      return Arguments.of(testName, assertion);
    }
  }
}
