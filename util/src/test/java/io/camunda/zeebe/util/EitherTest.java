/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.function.Function;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

@RunWith(Parameterized.class)
public class EitherTest {

  @Parameter(0)
  public Object value;

  @Parameterized.Parameters(name = "Either value {0}")
  public static Object[][] parameters() {
    return new Object[][] {
      {1},
      {123L},
      {123.456},
      {'c'},
      {"something"},
      {"bytes".getBytes()},
      {List.of(1, 2, 3)},
      {new Object[] {1, 2L, "3"}},
      {Either.right(1)},
      {Either.left(1)},
    };
  }

  @Test
  public void onlyARightValueCanBeRetrievedWithGet() {
    assertThat(Either.right(value).get()).isEqualTo(value);
    assertThatThrownBy(() -> Either.left(value).get()).isInstanceOf(NoSuchElementException.class);
  }

  @Test
  public void onlyALeftValueCanBeRetrievedWithGetLeft() {
    assertThat(Either.left(value).getLeft()).isEqualTo(value);
    assertThatThrownBy(() -> Either.right(value).getLeft())
        .isInstanceOf(NoSuchElementException.class);
  }

  @Test
  public void onlyARightIsRight() {
    assertThat(Either.right(value).isRight()).isTrue();
    assertThat(Either.left(value).isRight()).isFalse();
  }

  @Test
  public void onlyALeftIsLeft() {
    assertThat(Either.left(value).isLeft()).isTrue();
    assertThat(Either.right(value).isLeft()).isFalse();
  }

  @Test
  public void onlyARightIsTransformedByMap() {
    final Function<Object, String> mapper = o -> "Transformed-" + o.toString();
    final String mappedValue = mapper.apply(value);
    assertThat(mappedValue).isNotEqualTo(value);
    assertThat(Either.right(value).map(mapper)).isEqualTo(Either.right(mappedValue));
    assertThat(Either.left(value).map(mapper)).isEqualTo(Either.left(value));
  }

  @Test
  public void onlyALeftIsTransformedByMapLeft() {
    final Function<Object, String> mapper = o -> "Transformed-" + o.toString();
    final String mappedValue = mapper.apply(value);
    assertThat(mappedValue).isNotEqualTo(value);
    assertThat(Either.left(value).mapLeft(mapper)).isEqualTo(Either.left(mappedValue));
    assertThat(Either.right(value).mapLeft(mapper)).isEqualTo(Either.right(value));
  }

  @Test
  public void onlyARightIsTransformedByFlatMap() {
    assertThat(Either.right(value).flatMap(Either::left)).isEqualTo(Either.left(value));
    assertThat(Either.left(value).flatMap(Either::right)).isEqualTo(Either.left(value));
  }

  @Test
  public void onlyARightIsConsumedByIfRight() {
    final var verifiableConsumer = new VerifiableConsumer();
    Either.right(value).ifRight(verifiableConsumer);
    assertThat(verifiableConsumer.hasBeenExecuted).isTrue();
    Either.left(value).ifRight(new FailConsumer());
  }

  @Test
  public void onlyALeftIsConsumedByIfLeft() {
    final var verifiableConsumer = new VerifiableConsumer();
    Either.left(value).ifLeft(verifiableConsumer);
    assertThat(verifiableConsumer.hasBeenExecuted).isTrue();
    Either.right(value).ifLeft(new FailConsumer());
  }

  @Test
  public void onlyOneSideIsConsumedByIfRightOrLeft() {
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
}
