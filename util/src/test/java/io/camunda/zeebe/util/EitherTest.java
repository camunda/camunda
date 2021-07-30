/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util;

import static io.camunda.zeebe.util.EitherAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class EitherTest {

  static Stream<Object> parameters() {
    return Stream.of(
        1,
        123L,
        123.456,
        'c',
        "something",
        "bytes".getBytes(),
        List.of(1, 2, 3),
        new Object[] {1, 2L, "3"},
        Either.right(1),
        Either.left(1));
  }

  static Stream<Collection<Either<Object, Object>>> collections() {
    return parameters()
        .flatMap(
            value ->
                Stream.of(
                    List.of(),
                    List.of(Either.right(value)),
                    List.of(Either.left(value)),
                    List.of(Either.right(value), Either.right(value)),
                    List.of(Either.right(value), Either.left(value)),
                    List.of(Either.left(value), Either.right(value)),
                    List.of(Either.left(value), Either.left(value)),
                    List.of(Either.right(value), Either.right(value), Either.right(value)),
                    List.of(Either.right(value), Either.right(value), Either.left(value)),
                    List.of(Either.right(value), Either.left(value), Either.right(value)),
                    List.of(Either.right(value), Either.left(value), Either.left(value)),
                    List.of(Either.left(value), Either.right(value), Either.right(value)),
                    List.of(Either.left(value), Either.right(value), Either.left(value)),
                    List.of(Either.left(value), Either.left(value), Either.right(value)),
                    List.of(Either.left(value), Either.left(value), Either.left(value))));
  }

  static Stream<Collection<Either<Object, Object>>> collectionsWithoutLefts() {
    return collections().filter(c -> c.stream().noneMatch(Either::isLeft));
  }

  static Stream<Collection<Either<Object, Object>>> collectionsWithLefts() {
    return collections().filter(c -> c.stream().anyMatch(Either::isLeft));
  }

  @DisplayName("Only a Right value can be retrieved with .get()")
  @ParameterizedTest
  @MethodSource("parameters")
  void onlyARightValueCanBeRetrievedWithGet(final Object value) {
    assertThat(Either.right(value).get()).isEqualTo(value);
    assertThatThrownBy(() -> Either.left(value).get()).isInstanceOf(NoSuchElementException.class);
  }

  @DisplayName("Only a Left value can be retrieved with .getLeft()")
  @ParameterizedTest
  @MethodSource("parameters")
  void onlyALeftValueCanBeRetrievedWithGetLeft(final Object value) {
    assertThat(Either.left(value).getLeft()).isEqualTo(value);
    assertThatThrownBy(() -> Either.right(value).getLeft())
        .isInstanceOf(NoSuchElementException.class);
  }

  @DisplayName("Only a Right is Right")
  @ParameterizedTest
  @MethodSource("parameters")
  void onlyARightIsRight(final Object value) {
    assertThat(Either.right(value)).isRight();
    assertThat(Either.left(value)).isNotRight();
  }

  @DisplayName("Only a Left is Left")
  @ParameterizedTest
  @MethodSource("parameters")
  void onlyALeftIsLeft(final Object value) {
    assertThat(Either.left(value)).isLeft();
    assertThat(Either.right(value)).isNotLeft();
  }

  @DisplayName("Only a Right is transformed by .map(..)")
  @ParameterizedTest
  @MethodSource("parameters")
  void onlyARightIsTransformedByMap(final Object value) {
    final Function<Object, String> mapper = o -> "Transformed-" + o.toString();
    final String mappedValue = mapper.apply(value);
    assertThat(mappedValue).isNotEqualTo(value);
    assertThat(Either.right(value).map(mapper)).isEqualTo(Either.right(mappedValue));
    assertThat(Either.left(value).map(mapper)).isEqualTo(Either.left(value));
  }

  @DisplayName("Only a Left is transformed by .mapLeft(..)")
  @ParameterizedTest
  @MethodSource("parameters")
  void onlyALeftIsTransformedByMapLeft(final Object value) {
    final Function<Object, String> mapper = o -> "Transformed-" + o.toString();
    final String mappedValue = mapper.apply(value);
    assertThat(mappedValue).isNotEqualTo(value);
    assertThat(Either.left(value).mapLeft(mapper)).isEqualTo(Either.left(mappedValue));
    assertThat(Either.right(value).mapLeft(mapper)).isEqualTo(Either.right(value));
  }

  @DisplayName("Only a Right is transformed by .flatMap(..)")
  @ParameterizedTest
  @MethodSource("parameters")
  void onlyARightIsTransformedByFlatMap(final Object value) {
    assertThat(Either.right(value).flatMap(Either::left)).isEqualTo(Either.left(value));
    assertThat(Either.left(value).flatMap(Either::right)).isEqualTo(Either.left(value));
  }

  @DisplayName("Only a Right is consumed by .ifRight(..)")
  @ParameterizedTest
  @MethodSource("parameters")
  void onlyARightIsConsumedByIfRight(final Object value) {
    final var verifiableConsumer = new VerifiableConsumer();
    Either.right(value).ifRight(verifiableConsumer);
    assertThat(verifiableConsumer.hasBeenExecuted).isTrue();
    Either.left(value).ifRight(new FailConsumer());
  }

  @DisplayName("Only a Left is consumed by .ifLeft(..)")
  @ParameterizedTest
  @MethodSource("parameters")
  void onlyALeftIsConsumedByIfLeft(final Object value) {
    final var verifiableConsumer = new VerifiableConsumer();
    Either.left(value).ifLeft(verifiableConsumer);
    assertThat(verifiableConsumer.hasBeenExecuted).isTrue();
    Either.right(value).ifLeft(new FailConsumer());
  }

  @DisplayName("Only one side is consumed by .ifRightOrLeft(..)")
  @ParameterizedTest
  @MethodSource("parameters")
  void onlyOneSideIsConsumedByIfRightOrLeft(final Object value) {
    final var rightConsumer = new VerifiableConsumer();
    Either.right(value).ifRightOrLeft(rightConsumer, new FailConsumer());
    assertThat(rightConsumer.hasBeenExecuted).isTrue();

    final var leftConsumer = new VerifiableConsumer();
    Either.left(value).ifRightOrLeft(new FailConsumer(), leftConsumer);
    assertThat(leftConsumer.hasBeenExecuted).isTrue();
  }

  /** Simple Consumer that knows whether it's been executed. */
  private static final class VerifiableConsumer implements Consumer<Object> {
    boolean hasBeenExecuted = false;

    @Override
    public void accept(final Object o) {
      hasBeenExecuted = true;
    }
  }

  /** Simple Consumer that always fails a test when executed. */
  private static class FailConsumer implements Consumer<Object> {
    @Override
    public void accept(final Object o) {
      Assertions.fail("Expected NOT to perform this action!");
    }
  }

  @DisplayName("Streams of Eithers can be collected using .collector()")
  @Nested
  class CollectorTests {

    @DisplayName("Only Streams without Lefts are collected into a Right")
    @ParameterizedTest
    @MethodSource("io.camunda.zeebe.util.EitherTest#collectionsWithoutLefts")
    void onlyStreamsWithoutLeftsAreCollectedIntoARight(
        final List<Either<Object, Object>> collection) {
      assertThat(collection.stream().collect(Either.collector()))
          .isRight()
          .extracting(Either::get)
          .isEqualTo(
              collection.stream()
                  .filter(Predicate.not(Either::isLeft))
                  .map(Either::get)
                  .collect(Collectors.toList()));
    }

    @DisplayName("Only Streams with Lefts are collected into a Left")
    @ParameterizedTest
    @MethodSource("io.camunda.zeebe.util.EitherTest#collectionsWithLefts")
    void onlyStreamsWithLeftsAreCollectedIntoALeft(final List<Either<Object, Object>> collection) {
      assertThat(collection.stream().collect(Either.collector()))
          .isLeft()
          .extracting(Either::getLeft)
          .isEqualTo(
              collection.stream()
                  .filter(Either::isLeft)
                  .map(Either::getLeft)
                  .collect(Collectors.toList()));
    }
  }

  @DisplayName("Streams of Eithers can be collected using .collectorFoldingLeft()")
  @Nested
  class CollectorFoldingLeftTests {

    @DisplayName("Only Streams without Lefts are collected into a Right")
    @ParameterizedTest
    @MethodSource("io.camunda.zeebe.util.EitherTest#collectionsWithoutLefts")
    void onlyStreamsWithoutLeftsAreCollectedIntoARight(
        final List<Either<Object, Object>> collection) {
      assertThat(collection.stream().collect(Either.collectorFoldingLeft()))
          .isRight()
          .extracting(Either::get)
          .isEqualTo(
              collection.stream()
                  .filter(Predicate.not(Either::isLeft))
                  .map(Either::get)
                  .collect(Collectors.toList()));
    }

    @DisplayName("Only Streams with Lefts are collected into a Left")
    @ParameterizedTest
    @MethodSource("io.camunda.zeebe.util.EitherTest#collectionsWithLefts")
    void onlyStreamsWithLeftsAreCollectedIntoALeft(final List<Either<Object, Object>> collection) {
      assertThat(collection.stream().collect(Either.collectorFoldingLeft()))
          .isLeft()
          .extracting(Either::getLeft)
          .isEqualTo(
              collection.stream()
                  .filter(Either::isLeft)
                  .map(Either::getLeft)
                  .collect(Collectors.toList())
                  .get(0));
    }
  }
}
