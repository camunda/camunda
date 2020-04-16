/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Function;

/**
 * Represents either a {@link Left} or a {@link Right}. By convention, right is used for success and
 * left for error.
 *
 * <p>Some usage examples:
 *
 * <pre>
 * Either.right(1).get() // => 1
 * Either.left("an error occurred").getLeft() // => "an error occurred"
 * </pre>
 *
 * A right cannot be left (and vice-versa), so you'll need to check it at runtime:
 *
 * <pre>{@code
 * Either<String, Integer> x = Either.right(1);
 * if (x.isRight()) { // is true
 *   x.getLeft(); // throws NoSuchElementException
 * }
 * }</pre>
 *
 * @param <L> The left type
 * @param <R> The right type
 */
public interface Either<L, R> {

  /**
   * Returns a {@link Right} describing the given value.
   *
   * @param right the value to describe
   * @param <L> the type of the left value
   * @param <R> the type of the right value
   * @return a {@link Right} of the value
   */
  static <L, R> Either<L, R> right(R right) {
    return new Right<>(right);
  }

  /**
   * Returns a {@link Left} describing the given value.
   *
   * @param left the value to describe
   * @param <L> the type of the left value
   * @param <R> the type of the right value
   * @return a {@link Left} of the value
   */
  static <L, R> Either<L, R> left(L left) {
    return new Left<>(left);
  }

  /**
   * Returns true if this Either is a {@link Right}.
   *
   * @return true if right, false if left
   */
  boolean isRight();

  /**
   * Returns true if this Either is a {@link Left}.
   *
   * @return true if left, false if right
   */
  boolean isLeft();

  /**
   * Returns the right value, if this is a {@link Right}.
   *
   * @return the right value
   * @throws NoSuchElementException if this is a {@link Left}
   */
  R get();

  /**
   * Returns the left value, if this is a {@link Left}.
   *
   * @return the left value
   * @throws NoSuchElementException if this is a {@link Right}
   */
  L getLeft();

  /**
   * Maps the right value, if this is a {@link Right}.
   *
   * @param right the mapping function for the right value
   * @param <T> the type of the resulting right value
   * @return a mapped {@link Right} or the same {@link Left}
   */
  <T> Either<L, T> map(Function<? super R, ? extends T> right);

  /**
   * Maps the left value, if this is a {@link Left}.
   *
   * @param left the mapping function for the left value
   * @param <T> the type of the resulting left value
   * @return a mapped {@link Left} or the same {@link Right}
   */
  <T> Either<T, R> mapLeft(Function<? super L, ? extends T> left);

  final class Right<L, R> implements Either<L, R> {

    private final R value;

    private Right(final R value) {
      this.value = value;
    }

    @Override
    public boolean isRight() {
      return true;
    }

    @Override
    public boolean isLeft() {
      return false;
    }

    @Override
    public R get() {
      return this.value;
    }

    @Override
    public L getLeft() {
      throw new NoSuchElementException("Expected a left, but this is right");
    }

    @Override
    public <T> Either<L, T> map(final Function<? super R, ? extends T> right) {
      return Either.right(right.apply(this.value));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Either<T, R> mapLeft(final Function<? super L, ? extends T> left) {
      return (Either<T, R>) this;
    }

    @Override
    public int hashCode() {
      return Objects.hash(value);
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final Right<?, ?> right = (Right<?, ?>) o;
      return Objects.equals(value, right.value);
    }

    @Override
    public String toString() {
      return "Right[" + value + ']';
    }
  }

  final class Left<L, R> implements Either<L, R> {

    private final L value;

    private Left(final L value) {
      this.value = value;
    }

    @Override
    public boolean isRight() {
      return false;
    }

    @Override
    public boolean isLeft() {
      return true;
    }

    @Override
    public R get() {
      throw new NoSuchElementException("Expected a right, but this is left");
    }

    @Override
    public L getLeft() {
      return this.value;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Either<L, T> map(final Function<? super R, ? extends T> right) {
      return (Either<L, T>) this;
    }

    @Override
    public <T> Either<T, R> mapLeft(final Function<? super L, ? extends T> left) {
      return Either.left(left.apply(this.value));
    }

    @Override
    public int hashCode() {
      return Objects.hash(value);
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final Left<?, ?> left = (Left<?, ?>) o;
      return Objects.equals(value, left.value);
    }

    @Override
    public String toString() {
      return "Left[" + value + ']';
    }
  }
}
