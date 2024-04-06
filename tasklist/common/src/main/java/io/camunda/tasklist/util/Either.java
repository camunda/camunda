/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.tasklist.util;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collector;

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

  @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
  final class EitherOptional<R> {

    private final Optional<R> right;

    private EitherOptional(final Optional<R> right) {
      this.right = right;
    }

    public <L> Either<L, R> orElse(final L left) {
      return right.<Either<L, R>>map(Either::right).orElse(Either.left(left));
    }
  }
}
