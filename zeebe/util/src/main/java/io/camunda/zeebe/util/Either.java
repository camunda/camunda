/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util;

import io.camunda.zeebe.util.collection.Tuple;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collector;

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
public sealed interface Either<L, R> {

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
   * Convenience method to convert an {@link Optional<R>} of R to an {@code Either<?, R>}, using an
   * intermediary representation of the Optional in the form of {@link EitherOptional}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Either.ofOptional(Optional.of(1))
   *   .orElse("left value")
   *   .ifRightOrLeft(
   *     right -> System.out.println("If Optional is present, right is the contained value"),
   *     left -> System.out.println("If Optional is empty, left is the value provided by orElse")
   *   );
   * }</pre>
   *
   * @param right The optional that may contain the right value
   * @param <R> the type of the right value
   * @return An intermediary representation {@link EitherOptional}
   */
  @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
  static <R> EitherOptional<R> ofOptional(final Optional<R> right) {
    return new EitherOptional<>(right);
  }

  /**
   * Returns a collector for {@code Either<L,R>} that collects them into {@code
   * Either<List<L>,List<R>} and favors {@link Left} over {@link Right}.
   *
   * <p>This is commonly used to collect a stream of either objects where a right is considered a
   * success and a left is considered an error. If any error has occurred, we're often only
   * interested in the errors and not in the successes. Otherwise, we'd like to collect all success
   * values.
   *
   * <p>This collector groups all the lefts into one {@link List} and all the rights into another.
   * When all elements of the stream have been collected and any lefts were encountered, it outputs
   * a left of the list of encountered left values. Otherwise, it outputs a right of all right
   * values it encountered.
   *
   * <p>Examples:
   *
   * <pre>{@code
   * Stream.of(Either.right(1), Either.right(2), Either.right(3))
   *   .collect(Either.collector()) // => a Right
   *   .get(); // => List.of(1,2,3)
   *
   * Stream.of(Either.right(1), Either.left("oops"), Either.right(3))
   *   .collect(Either.collector()) // => a Left
   *   .getLeft(); // => List.of("oops")
   * }</pre>
   *
   * @param <L> the type of the left values
   * @param <R> the type of the right values
   * @return a collector that favors left over right
   */
  static <L, R>
      Collector<Either<L, R>, Tuple<List<L>, List<R>>, Either<List<L>, List<R>>> collector() {
    return Collector.of(
        () -> new Tuple<>(new ArrayList<>(), new ArrayList<>()),
        (acc, next) ->
            next.ifRightOrLeft(right -> acc.getRight().add(right), left -> acc.getLeft().add(left)),
        (a, b) -> {
          a.getLeft().addAll(b.getLeft());
          a.getRight().addAll(b.getRight());
          return a;
        },
        acc -> {
          if (!acc.getLeft().isEmpty()) {
            return left(acc.getLeft());
          } else {
            return right(acc.getRight());
          }
        });
  }

  /**
   * Returns a collector for {@code Either<L,R>} that collects them into {@code Either<L,List<R>}
   * and favors {@link Left} over {@link Right}. While collecting the rights, it folds the left into
   * the first encountered.
   *
   * <p>This is commonly used to collect a stream of either objects where a right is considered a
   * success and a left is considered an error. If any error has occurred, we're often only
   * interested in the first error and not in the successes. Otherwise, we'd like to collect all
   * success values.
   *
   * <p>This collector looks for a left while it groups all the rights into one {@link List}. When
   * all elements of the stream have been collected and a left was encountered, it outputs the
   * encountered left. Otherwise, it outputs a right of all right values it encountered.
   *
   * <p>Examples:
   *
   * <pre>{@code
   * * Stream.of(Either.right(1), Either.right(2), Either.right(3))
   * *   .collect(Either.collector()) // => a Right
   * *   .get(); // => List.of(1,2,3)
   * *
   * * Stream.of(Either.right(1), Either.left("oops"), Either.left("another oops"))
   * *   .collect(Either.collector()) // => a Left
   * *   .getLeft(); // => "oops"
   * *
   * }</pre>
   *
   * @param <L> the type of the left values
   * @param <R> the type of the right values
   * @return a collector that favors left over right
   */
  static <L, R>
      Collector<Either<L, R>, Tuple<Optional<L>, List<R>>, Either<L, List<R>>>
          collectorFoldingLeft() {
    return Collector.of(
        () -> new Tuple<>(Optional.empty(), new ArrayList<>()),
        (acc, next) ->
            next.ifRightOrLeft(
                right -> acc.getRight().add(right),
                left -> acc.setLeft(acc.getLeft().or(() -> Optional.of(left)))),
        (a, b) -> {
          if (a.getLeft().isEmpty() && b.getLeft().isPresent()) {
            a.setLeft(b.getLeft());
          }
          a.getRight().addAll(b.getRight());
          return a;
        },
        acc -> acc.getLeft().map(Either::<L, List<R>>left).orElse(Either.right(acc.getRight())));
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
   * Returns the right value, or a default value if this is a {@link Left}.
   *
   * @param defaultValue the default value
   * @return the right value, or the default value if this is a {@link Left}
   */
  R getOrElse(R defaultValue);

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
   * Executes the given action with the right value if this is a {@link Right}, otherwise does
   * nothing. This method facilitates side-effect operations on the right value without altering the
   * state or the type of the {@code Either}. After executing the action, the original {@code
   * Either<L, R>} is returned, allowing for further chaining of operations in a fluent API style.
   *
   * <p>When the instance is a {@link Right}, the action is executed, and the original {@code
   * Either<L, R>} is returned. This maintains the right value's type and allows the action to be
   * performed as a side-effect without changing the outcome. When the instance is a {@link Left},
   * no action is performed, and the {@code Left} instance is returned unchanged, preserving the
   * error information.
   *
   * <p>Usage example when {@code Either} is a {@link Right}:
   *
   * <pre>{@code
   * Either<Exception, String> rightEither = Either.right("Success");
   * Either<Exception, String> result = rightEither.thenDo(value -> System.out.println("Processed value: " + value));
   * // Output: Processed value: Success
   * // result remains a Right<Exception, String>, with the value "Success"
   * }</pre>
   *
   * <p>Usage example when {@code Either} is a {@link Left}:
   *
   * <pre>{@code
   * Either<String, Integer> leftEither = Either.left("Error occurred");
   * Either<String, Integer> result = leftEither.thenDo(value -> System.out.println("This will not be printed"));
   * // No output as the action is not executed
   * // result remains an unchanged Left<String, Integer>, containing the original error message
   * }</pre>
   *
   * @param action the consuming function to perform with the right value, if present
   * @return Either<L, R> the original Either instance, allowing further operations.
   */
  Either<L, R> thenDo(Consumer<R> action);

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
   * Maps the right or left value into a new type using the provided functions, depending on whether
   * this is a {@link Left} or {@link Right}.
   *
   * <p>A common use case is to map to a new common value in success and error cases. Example:
   *
   * <pre>{@code
   * Either<String, Integer> success = Either.right(42); // => Right(42)
   * Either<String, Integer> failure = Either.left("Error occurred"); // => Left("Error occurred")
   *
   * var rightFn = result -> "Success: " + result;
   * var leftFn = error -> "Failure: " + error;
   *
   * success.fold(leftFn, rightFn); // => "Success: 42"
   * failure.fold(leftFn, rightFn); // => "Failure: Error occurred"
   * }</pre>
   *
   * @param leftFn the mapping function for the left value
   * @param rightFn the mapping function for the right value
   * @return either a mapped {@link Left} or {@link Right}, folded to the new type
   * @param <T> the type of the resulting value
   */
  <T> T fold(Function<? super L, ? extends T> leftFn, Function<? super R, ? extends T> rightFn);

  /**
   * A right for either a left or right. By convention, right is used for success and left for
   * error.
   *
   * @param <L> The left type
   * @param <R> The right type
   */
  @SuppressWarnings("java:S2972")
  record Right<L, R>(R value) implements Either<L, R> {
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
    public R getOrElse(final R defaultValue) {
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

    @Override
    public <T> Either<L, T> flatMap(final Function<? super R, ? extends Either<L, T>> right) {
      return right.apply(value);
    }

    @Override
    public Either<L, R> thenDo(final Consumer<R> action) {
      action.accept(value);
      return this;
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
    public <T> T fold(
        final Function<? super L, ? extends T> leftFn,
        final Function<? super R, ? extends T> rightFn) {
      return rightFn.apply(value);
    }
  }

  /**
   * A left for either a left or right. By convention, right is used for success and left for error.
   *
   * @param <L> The left type
   * @param <R> The right type
   */
  @SuppressWarnings("java:S2972")
  record Left<L, R>(L value) implements Either<L, R> {

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
    public R getOrElse(final R defaultValue) {
      return defaultValue;
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

    @Override
    @SuppressWarnings("unchecked")
    public <T> Either<L, T> flatMap(final Function<? super R, ? extends Either<L, T>> right) {
      return (Either<L, T>) this;
    }

    @Override
    public Either<L, R> thenDo(final Consumer<R> action) {
      return this;
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
    public <T> T fold(
        final Function<? super L, ? extends T> leftFn,
        final Function<? super R, ? extends T> rightFn) {
      return leftFn.apply(value);
    }
  }

  record EitherOptional<R>(Optional<R> right) {
    public <L> Either<L, R> orElse(final L left) {
      return right.<Either<L, R>>map(Either::right).orElse(Either.left(left));
    }
  }
}
