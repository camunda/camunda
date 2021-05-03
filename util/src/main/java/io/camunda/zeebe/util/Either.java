/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.util;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;
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
 * Either works great if you have complex logic that needs to be executed under complex conditions.
 * Consider the following code example, in which both method1 and method2 return Either a Right if
 * the method was successful, or a Left if the method failed somehow.
 *
 * <pre>{@code
 * method1()
 *   .flatMap(i -> method2(i))
 *   .ifRightOrLeft(
 *     ok -> System.out.println("Both methods were successful"),
 *     error -> System.err.println("Either method1 or method2 failed")
 *   )
 * }</pre>
 *
 * In this example, method2 is called if and only if method1 returned a Right (i.e. was successful).
 * Then to finish, a line is printed to std out or err depending on the successful/failure of the
 * called methods. Note, that the flatMap can also be used to change the type of the Right value.
 * Also note that the mapping functions can be chained to further change the resulting values at any
 * stage of the call chain.
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
  static <L, R> Either<L, R> right(final R right) {
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
  static <L, R> Either<L, R> left(final L left) {
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

  /**
   * Flatmaps the right value into a new Either, if this is a {@link Right}.
   *
   * <p>A common use case is to map a right value to a new right, unless some error occurs in which
   * case the value can be mapped to a new left. Note that this flatMap does not allow to alter the
   * type of the left side. Example:
   *
   * <pre>{@code
   * Either.<String, Integer>right(0) // => Right(0)
   *   .flatMap(x -> Either.right(x + 1)) // => Right(1)
   *   .flatMap(x -> Either.left("an error occurred")) // => Left("an error occurred")
   *   .getLeft(); // => "an error occurred"
   * }</pre>
   *
   * @param right the flatmapping function for the right value
   * @param <T> the type of the right side of the resulting either
   * @return either a mapped {@link Right} or a new {@link Left} if this is a right; otherwise the
   *     same left, but cast to consider the new type of the right.
   */
  <T> Either<L, T> flatMap(Function<? super R, ? extends Either<L, T>> right);

  /**
   * Performs the given action with the value if this is a {@link Right}, otherwise does nothing.
   *
   * @param action the consuming function for the right value
   */
  void ifRight(Consumer<R> action);

  /**
   * Performs the given action with the value if this is a {@link Left}, otherwise does nothing.
   *
   * @param action the consuming function for the left value
   */
  void ifLeft(Consumer<L> action);

  /**
   * Performs the given right action with the value if this is a {@link Right}, otherwise performs
   * the given left action with the value.
   *
   * @param rightAction the consuming function for the right value
   * @param leftAction the consuming function for the left value
   */
  void ifRightOrLeft(Consumer<R> rightAction, Consumer<L> leftAction);

  /**
   * A right for either a left or right. By convention, right is used for success and left for
   * error.
   *
   * @param <L> The left type
   * @param <R> The right type
   */
  @SuppressWarnings("java:S2972")
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
      return value;
    }

    @Override
    public L getLeft() {
      throw new NoSuchElementException("Expected a left, but this is right");
    }

    @Override
    public <T> Either<L, T> map(final Function<? super R, ? extends T> right) {
      return Either.right(right.apply(value));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Either<T, R> mapLeft(final Function<? super L, ? extends T> left) {
      return (Either<T, R>) this;
    }

    public <T> Either<L, T> flatMap(final Function<? super R, ? extends Either<L, T>> right) {
      return right.apply(value);
    }

    @Override
    public void ifRight(final Consumer<R> right) {
      right.accept(value);
    }

    @Override
    public void ifLeft(final Consumer<L> action) {
      // do nothing
    }

    @Override
    public void ifRightOrLeft(final Consumer<R> rightAction, final Consumer<L> leftAction) {
      rightAction.accept(value);
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

  /**
   * A left for either a left or right. By convention, right is used for success and left for error.
   *
   * @param <L> The left type
   * @param <R> The right type
   */
  @SuppressWarnings("java:S2972")
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
      return value;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Either<L, T> map(final Function<? super R, ? extends T> right) {
      return (Either<L, T>) this;
    }

    @Override
    public <T> Either<T, R> mapLeft(final Function<? super L, ? extends T> left) {
      return Either.left(left.apply(value));
    }

    @SuppressWarnings("unchecked")
    public <T> Either<L, T> flatMap(final Function<? super R, ? extends Either<L, T>> right) {
      return (Either<L, T>) this;
    }

    @Override
    public void ifRight(final Consumer<R> right) {
      // do nothing
    }

    @Override
    public void ifLeft(final Consumer<L> action) {
      action.accept(value);
    }

    @Override
    public void ifRightOrLeft(final Consumer<R> rightAction, final Consumer<L> leftAction) {
      leftAction.accept(value);
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
